package de.locked.cellmapper;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ChooseAccountActivity extends ListActivity  {
//    private static final String LOG_TAG = ChooseAccountActivity.class.getName();

    protected AccountManager accountManager;
    protected Intent intent;

    /** Called when the activity is first created. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        
        accountManager = AccountManager.get(getApplicationContext());
        Account[] accounts = accountManager.getAccountsByType("com.google");
        this.setListAdapter(new ArrayAdapter(this, android.R.layout.test_list_item, accounts));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Account account = (Account) getListView().getItemAtPosition(position);
        Intent intent = new Intent(this, AppInfo.class);
        intent.putExtra("account", account);
        startActivity(intent);
    }
}
