package de.locked.cellmapper.model;



public class Strings {

    /**
     * Append str to s as long as s.length is less than l
     * 
     * @param s
     * @param l
     * @param str
     * @return
     */
    public static String rpad(String s, int l, String str) {
        while (s.length() < l) {
            s += str;
        }
        return s;
    }

}
