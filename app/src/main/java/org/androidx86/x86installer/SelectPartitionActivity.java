package org.androidx86.x86installer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SelectPartitionActivity extends ActionBarActivity {

    private String localIsoURI;
    private String targetPartition;
    private InstallService installService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_partition);
        Intent intent = getIntent();
        localIsoURI = intent.getStringExtra(MainActivity.EXTRA_LOCAL_ISOFILE);
        installService = new InstallService();

        final ExpandableListView partitionslistView = (ExpandableListView) findViewById(R.id.partitionsListView);
        partitionslistView.setAdapter(createDisksViewItems(installService.listPartitions()));
        partitionslistView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView adapterView, View view, int groupPos, int childPos, long id) {
                int flatListPosition = adapterView.getFlatListPosition(adapterView.getPackedPositionForChild(groupPos, childPos));
                Map<String, String> selectedPartition = (Map<String, String>) adapterView.getItemAtPosition(flatListPosition);
                if (selectedPartition != null) {
                    targetPartition = selectedPartition.get("titre");

                    view.setSelected(true);
                    //partitionslistView.setSelectedChild(groupPos, childPos, true);
                    findViewById(R.id.install_button).setEnabled(true);
                    return true;
                }
                return false;
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_select_partition, menu);
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

    public void installAndroid(View view){
        final Handler handler = new Handler();

        final boolean formatPartition = ((CheckBox)findViewById(R.id.formatPartitionCheckBox)).isChecked();
        final boolean installGrub = ((CheckBox)findViewById(R.id.installBootloaderCheckBox)).isChecked();
        final boolean isISOFile = localIsoURI.toUpperCase().endsWith(".ISO");

        final Runnable installTask = new Runnable() {
            @Override
            public void run() {
                DiskPartition diskPartition = DiskPartition.createFromBlockDeviceName(targetPartition);

                final File temporaryDownloadFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                StringBuilder outputBuiler = new StringBuilder();

                try {
                    InstallService installService = new InstallService();
                    String androidVersion = "android-x86";
                    updateInstallProgress(handler, outputBuiler, "Mounting installation media: " + localIsoURI);
                    if (isISOFile) {
                        androidVersion = localIsoURI.substring(localIsoURI.lastIndexOf("/") + 1, localIsoURI.length() - 4);
                        installService.mountIsoFile(localIsoURI);
                    } else {
                        installService.tryMountCdrom();
                    }

                    if (formatPartition) {
                        updateInstallProgress(handler, outputBuiler, "Formatting partition: " + targetPartition);
                        installService.formatPartition(diskPartition);
                    }
                    updateInstallProgress(handler, outputBuiler, "Mounting partition: " + targetPartition);
                    installService.mountPartition(targetPartition);
                    updateInstallProgress(handler, outputBuiler, "Installing on partition: " + targetPartition);
                    installService.installOnPartition(androidVersion, targetPartition);
                    updateInstallProgress(handler, outputBuiler, "Unmounting installation media");
                    installService.unmountInstallationMedia();
                    updateInstallProgress(handler, outputBuiler, "Expanding System.img on disk for read/write and root access");
                    installService.expandSystemOnDisk(androidVersion);
                    updateInstallProgress(handler, outputBuiler, "Copying GRUB Bootloader file under /boot/grub/ on partition " + targetPartition);
                    installService.copyGrub2FilesOnPartition(androidVersion, getAssets(), temporaryDownloadFile, diskPartition);
                    if (installGrub) {
                        updateInstallProgress(handler, outputBuiler, "Install GRUB Bootloader on MBR: " + targetPartition);
                        installService.installBootloader(diskPartition);
                    }
                    //updateInstallProgress(handler, outputBuiler, "Unmounting partition: "+targetPartition);
                    //installService.unmountPartition(targetPartition);
                    updateInstallProgress(handler, outputBuiler, "Installation finished");
                }
                catch(RuntimeException exception){
                    updateInstallProgress(handler, outputBuiler, "Installation failed! "+exception.toString());
                    //throw exception;
                }
            }
        };
        new Thread(installTask).start();
    }

    private void updateInstallProgress(Handler handler, final StringBuilder outputBuiler, String text){
        outputBuiler.append(text+"\n");
        handler.post(new Runnable() {
            public void run() {
                TextView progressText = (TextView) findViewById(R.id.install_output);
                progressText.setText(outputBuiler.toString());
            }
        });
    }

    private ExpandableListAdapter createDisksViewItems(List<String> partitions){
        List<Map<String, String>> disksViewItems = new ArrayList<>();
        List<List<Map<String, String>>> partitionsList = new ArrayList<>();
        List<Map<String, String>> currentDiskPartitions = null;
        for (String partition : partitions){
            String[] line = partition.split("\\s+");
            String size = line[3];
            String name = line[4];
            if (Disk.isDisk(name)) {
                //disk name
                Map<String, String> viewItem = new HashMap<>();
                viewItem.put("diskname", name);
                disksViewItems.add(viewItem);
                currentDiskPartitions = new ArrayList<>();
                partitionsList.add(currentDiskPartitions);
            }
            else if (currentDiskPartitions != null && DiskPartition.isDiskPartition(name)){
                //partition name
                Map<String, String> viewItem = new HashMap<>();
                viewItem.put("titre", name);
                viewItem.put("description", (new BigDecimal(size).divide(new BigDecimal(1000000)).toString())+" Gb");
                currentDiskPartitions.add(viewItem);
            }
        }



        return new SimpleExpandableListAdapter(this,
                disksViewItems, R.layout.diskitem, new String[]{"diskname"}, new int[]{R.id.diskname},
                partitionsList, R.layout.diskpartitionitem, new String[]{"titre", "description"}, new int[]{R.id.titre, R.id.description});
    }

}
