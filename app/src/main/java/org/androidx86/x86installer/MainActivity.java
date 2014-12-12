package org.androidx86.x86installer;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;
import java.util.List;


public class MainActivity extends ActionBarActivity {
    public final static String EXTRA_LOCAL_ISOFILE = "org.androidx86.x86installer.EXTRA_LOCAL_ISOFILE";

    private ListView isoFileListView;
    private long currentDownload;
    private String localIsoURI;

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putString(EXTRA_LOCAL_ISOFILE, localIsoURI);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            localIsoURI = savedInstanceState.getString(EXTRA_LOCAL_ISOFILE);
        }
        setContentView(R.layout.activity_main);

        loadMediaList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void downloadIsoFile(View view) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        registerReceiver(createIsoDownloadReceiver(dm), new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        //http://downloads.sourceforge.net/project/android-x86/Release%204.4/android-x86-4.4-r1.iso?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fandroid-x86%2Ffiles%2FRelease%25204.4%2Fandroid-x86-4.4-r1.iso%2Fdownload&ts=1417808194&use_mirror=netcologne
        DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse("http://sourceforge.net/projects/android-x86/files/Release%204.4/android-x86-4.4-r1.iso/download"));
        try {
            //Boolean isSDPresent = android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
            //if (isSDPresent) {
            currentDownload = dm.enqueue(request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "android-x86-4.4-r1.iso"));
            //} else {
            //    currentDownload = dm.enqueue(request.set);
            //}
        } catch (Throwable throwable) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("Download Failed");
            dialog.setMessage(throwable.getMessage());
            dialog.setNeutralButton("Ok", null);
            dialog.create().show();
        }
    }

    public void installOnDisk(View view) {
        if (localIsoURI != null && !localIsoURI.isEmpty()) {
            Intent installOnDiskIntent = new Intent(this, SelectPartitionActivity.class);
            installOnDiskIntent.putExtra(EXTRA_LOCAL_ISOFILE, localIsoURI);
            startActivity(installOnDiskIntent);
        }
    }

    private BroadcastReceiver createIsoDownloadReceiver(final DownloadManager downloadManager) {
        final BroadcastReceiver onDownloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    //long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    DownloadManager.Query query = new DownloadManager.Query().setFilterById(currentDownload);
                    Cursor c = downloadManager.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c
                                .getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            loadMediaList();
                        }
                    }
                }
            }
        };

        return onDownloadCompleteReceiver;
    }

    private void loadMediaList() {
        isoFileListView = (ListView) findViewById(R.id.isoFileslistView);

        InstallService installService = new InstallService();
        final List<InstallMedia> installMedias =
                installService.listInstallMedias(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        InstallMediaListViewAdapter isoFileItemAdapter = new InstallMediaListViewAdapter(this,
                R.layout.isofileitem, R.id.title,
                installMedias);

        isoFileListView.setAdapter(isoFileItemAdapter);
        isoFileListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        isoFileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                InstallMedia clickedItem = (InstallMedia) adapterView.getItemAtPosition(position);
                final String description = clickedItem.getDescription();
                final String titre = clickedItem.getTitle();
                if (titre.toUpperCase().endsWith(".ISO")) {
                    localIsoURI = new File(description + File.separator + titre).getAbsolutePath();
                }
                else {
                    localIsoURI = titre;
                }

                view.setSelected(true);
                findViewById(R.id.install_on_disk_button).setEnabled(true);
            }
        });
    }


}