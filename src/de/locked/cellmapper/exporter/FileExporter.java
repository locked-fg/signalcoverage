package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import android.database.Cursor;
import android.os.Environment;
import android.util.Log;

public class FileExporter implements DataExporter {
    private static final String LOG_TAG = FileExporter.class.getName();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String fileName;

    public FileExporter(String fileName) {
        this.fileName = fileName;
    }

    /**
     * @see de.locked.cellmapper.model.DataExporter#addPropertyChangeListener(java.beans.PropertyChangeListener)
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * @see de.locked.cellmapper.model.DataExporter#process(android.database.Cursor)
     */
    @Override
    public void process(Cursor cursor) {
        try {
            File root = Environment.getExternalStorageDirectory();
            if (!root.canWrite()) {
                return;
            }

            CsvFile csv = new CsvFile(fileName + ".csv");
            KmlFile kml = new KmlFile(fileName + ".kml");

            // select all data and dump it
            int n = 0;
            int latitude = cursor.getColumnIndex("latitude");
            int longitude = cursor.getColumnIndex("longitude");
            int accuracy = cursor.getColumnIndex("accuracy");
            int signalStrength = cursor.getColumnIndex("signalStrength");

            List<String> values = new ArrayList<String>();
            while (cursor.moveToNext()) {
                // write header
                if (n == 0) {
                    csv.writeHead(cursor.getColumnNames());
                }

                // Write values
                values.clear();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    values.add(cursor.getString(i));
                }
                csv.addLine(values);

                kml.addPoint(cursor.getFloat(longitude), cursor.getFloat(latitude), cursor.getFloat(signalStrength),
                        cursor.getFloat(accuracy));

                n++;
                // logging
                if (n % 100 == 0) {
                    Log.d(LOG_TAG, "wrote " + n + "lines");
                    pcs.firePropertyChange("progress", 0, n);
                }
            }
            Log.i(LOG_TAG, "wrote " + n + "lines");
            pcs.firePropertyChange("progress", 0, n);

            csv.close();
            kml.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "io exception", e);
        }
    }

    class KmlOutputStream extends FilterOutputStream {
        int offset = 0;

        public KmlOutputStream(File dest) throws IOException {
            super(new BufferedOutputStream(new FileOutputStream(dest, false), 100 * 1024));
        }

        public void append(String s) throws IOException {
            write(s.getBytes());
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            super.write(buffer, offset, length);
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            super.write(buffer);
        }
    }

    class KmlFile {

        private final KmlOutputStream os;
        // http://www.ig.utexas.edu/outreach/googleearth/latlong.html
        private final double mToDegree = 0.00001;
        private final float heightMultiplyer = 20;
        // values > this value will be replaced by the default
        private final int signalStrengthMaximum = 90;
        // replace signalstrength > threshold by this value
        private final int defaultSignalStrength = 0;

        public KmlFile(String fileName) throws IOException {
            File root = Environment.getExternalStorageDirectory();
            if (!root.canWrite()) {
                Log.e(LOG_TAG, "can't write to SD root: " + root.getAbsolutePath());
                throw new IOException("sd not writable");
            }

            File dest = new File(root, fileName);
            dest.getParentFile().mkdirs();
            dest.createNewFile();
            Log.i(LOG_TAG, "created " + dest.getAbsolutePath());

            os = new KmlOutputStream(dest);
            os.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
                    "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n" + //
                    "<Folder>\n" + //
                    "  <name>Signalstrength</name>\n" + //
                    "  <open>1</open>\n");
        }

        public void addPoint(float longitude, float latitude, float signalStrength, float accuracy) throws IOException {
            if (accuracy > 50)
                return;
            double halfAcc = (accuracy / 2d) * mToDegree;

            signalStrength = signalStrength > signalStrengthMaximum ? defaultSignalStrength : signalStrength;
            signalStrength *= heightMultiplyer;

            double leftLon = longitude - halfAcc;
            double rightLon = longitude + halfAcc;
            double topLat = latitude - halfAcc;
            double bottomLat = latitude + halfAcc;
            os.append("<Placemark>\n" + //
                    " <name>Signalstrength</name>\n" + //
                    "  <Polygon>\n" + //
                    "    <extrude>1</extrude>\n" + //
                    "    <altitudeMode>relativeToGround</altitudeMode>\n" + //
                    "    <outerBoundaryIs>\n");
            os.append("      <LinearRing>\n");
            os.append("        <coordinates>\n");

            os.append(leftLon + "," + topLat + "," + signalStrength + "\n"); // tl
            os.append(rightLon + "," + topLat + "," + signalStrength + "\n"); // tr
            os.append(rightLon + "," + bottomLat + "," + signalStrength + "\n"); // br
            os.append(leftLon + "," + bottomLat + "," + signalStrength + "\n"); // bl
            os.append(leftLon + "," + topLat + "," + signalStrength + "\n"); // tl

            os.append("        </coordinates>\n");
            os.append("      </LinearRing>\n");

            os.append("    </outerBoundaryIs>\n" + //
                    "  </Polygon>\n" + //
                    "</Placemark>\n");
        }

        public void close() throws IOException {
            if (os != null) {
                os.append("</Folder>\n" + //
                        "</kml>");
                os.close();
            }
        }
    }

    class CsvFile {

        private final OutputStreamWriter os;

        public CsvFile(String fileName) throws IOException {
            File root = Environment.getExternalStorageDirectory();
            if (!root.canWrite()) {
                Log.e(LOG_TAG, "can't write to SD root: " + root.getAbsolutePath());
                throw new IOException("sd not writable");
            }

            File dest = new File(root, fileName);
            dest.getParentFile().mkdirs();
            dest.createNewFile();
            Log.i(LOG_TAG, "created " + dest.getAbsolutePath());

            os = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(dest, false), 50 * 1024));
        }

        public void addLine(List<String> values) throws IOException {
            for (String val : values) {
                os.append(val == null ? "" : val).append(";");
            }
            os.append("\n");
        }

        public void writeHead(String[] columnNames) throws IOException {
            for (String name : columnNames) {
                os.append(name).append(";");
            }
            os.append("\n");
        }

        public void close() throws IOException {
            if (os != null) {
                os.close();
            }
        }
    }

}
