package org.androidx86.x86installer;

import android.content.res.AssetManager;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstallService {
    private final String cdromDevice = "/dev/block/sr0";

    public List<Map<String, String>> listInstallMedias(File androidDownloadDir){
        List<Map<String, String>> installMedias = new ArrayList<Map<String, String>>();
        if (tryMountCdrom()){
            File file = new File(cdromDevice);
            Map<String, String> map = new HashMap<String, String>();
            map.put("titre", "/dev/block/"+file.getName());
            map.put("description", "removable media");
            map.put("img", String.valueOf(R.drawable.ic_isofile));

            installMedias.add(map);
        }
        addIsoFiles(installMedias, androidDownloadDir);
        return installMedias;
    }

    private void addIsoFiles(List<Map<String, String>> installMedias, File parentDirectory) {
        FilenameFilter isoFileFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.toUpperCase().endsWith(".ISO")) {
                    return true;
                }
                return false;
            }
        };

        for (String isoFile : parentDirectory.list(isoFileFilter)) {
            File file = new File(isoFile);
            Map<String, String> map = new HashMap<String, String>();
            map.put("titre", file.getName());
            map.put("description", parentDirectory.getPath());
            map.put("img", String.valueOf(R.drawable.ic_isofile));

            installMedias.add(map);
        }
    }

    public List<String> listPartitions() {
        List<String> availablePartitions = new ArrayList<String>();
        //Run 'cat /proc/partitions'
        try {
            Process process = Runtime.getRuntime().exec("cat /proc/partitions");
            //Process process = Runtime.getRuntime().exec("su fdisk -l");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                output.append(line + "\n");
            }

            availablePartitions.addAll(Arrays.asList(output.toString().split("\\n")));
            availablePartitions.remove(0);
            availablePartitions.remove(0);
        }catch (IOException exception){
            throw new RuntimeException("Failed to read partitions: "+exception.getMessage());
        }
        return availablePartitions;
    }


    public void remountRootFsAsReadWrite(){
        //mount -o remount,rw /
        suCommand("mount -o remount,rw /");
        suCommand("mkdir "+getCdrom());
    }

    private String getCdrom(){
        return "/storage/cdrom";
    }

    /**
     * @return true if the cdrom have been mounted on /storage/cdrom
     */
    public boolean tryMountCdrom(){
        remountRootFsAsReadWrite();
        //mount -t iso9660 -r /dev/block/sr0
        String output = suCommand("mount -t iso9660 -r "+cdromDevice+" "+getCdrom());
        return output.isEmpty();
    }

    public void mountIsoFile(String isoFile){
        remountRootFsAsReadWrite();
        //mount -t iso9660 -o ro,loop /storage/sdcard0/Download/Android-x86-xx.iso
        suCommand("losetup -d /dev/block/loop0");
        suCommand("mount -t iso9660 -o ro,loop "+isoFile+" "+getCdrom());
    }

    public void unmountInstallationMedia(){
        suCommand("umount "+getCdrom());
    }

    private String getBlockDevice(String partition){
        return "/dev/block/"+partition;
    }

    public void mountPartition(String targetPartition) {
        final String targetBlockDevice = getBlockDevice(targetPartition);
        remountRootFsAsReadWrite();
        suCommand("mkdir /hd");
        mountPartiton(targetBlockDevice);
    }

    public void installOnPartition(String releaseVersion, String targetPartition) {
       final String installDirectory = "/hd/"+releaseVersion;
       final String dataDirectory = installDirectory+"/data";
       suCommand("mkdir "+installDirectory);
       suCommand("mkdir "+dataDirectory);
       suCommand("cp "+getCdrom()+"/kernel"+" "+installDirectory);
       suCommand("cp "+getCdrom()+"/initrd.img"+" "+installDirectory);
       suCommand("cp "+getCdrom()+"/ramdisk.img"+" "+installDirectory);
       suCommand("cp "+getCdrom()+"/system.sfs"+" "+installDirectory);
    }

    public void expandSystemOnDisk(String releaseVersion) {
        final String installDirectory = "/hd/"+releaseVersion;
        final String systemDirectory = installDirectory+"/system";
        final String tmpDirectory = "/hd/tmp";
        suCommand("mkdir "+systemDirectory);
        suCommand("mkdir "+tmpDirectory);
        //losetup -f
        //losetup -d /dev/block/loop0
        suCommand("losetup -d /dev/block/loop0");
        //mount -t squashfs -o,loop /hd/system.sfs /hd/tmp
        suCommand("mount -t squashfs -o,loop /hd/system.sfs "+tmpDirectory);
        suCommand("cp "+tmpDirectory+"/system.img "+installDirectory);
        suCommand("umount "+tmpDirectory);
        suCommand("losetup -d /dev/block/loop0");

        suCommand("mount -t ext4 -o,loop "+installDirectory+"/system.img /hd/tmp");
        suCommand("cp -R /hd/tmp/* "+systemDirectory);
        suCommand("umount "+tmpDirectory);
        suCommand("losetup -d /dev/block/loop0");
    }

    private String mountPartiton(String targetBlockDevice) {
        //mount -t ext2 targetBlockDevice /hd
        return suCommand("mount -t ext2 "+targetBlockDevice+" /hd");
    }

    public String formatPartition(String targetPartition) {
        final String targetBlockDevice = getBlockDevice(targetPartition);
        //fdisk: 'n' creates new partitions, 'd' delete partition, 'p' print partitions, 'q' quit, 'w' write and quit.
        //suCommand("echo -e \"p\\nq\\n\" | fdisk "+targetBlockDevice);
        //ext2: make2fs -L Name /dev/block/sda1
        //ext3: make2fs -jL Name /dev/block/sda1
        //ntfs: mkntfs -fL
        //fat32: newfs_msdos -L
        //exfat?
        //btrfs:?
        return suCommand("mke2fs -L AndroidX86 "+targetBlockDevice);
    }

    public void installGrubFiles(AssetManager assetManager, File downloadDirectory, String targetPartition) {
        //mkdir /hd/grub
        suCommand("mkdir /hd/grub");
        //touch /hd/grub/menu.lst
        suCommand("touch /hd/grub/menu.lst");

        //cp assets/* /hd/
        File directory = new File(downloadDirectory+File.separator+"grub");
        directory.mkdir();
        extractAssetFileInDirectory(assetManager, directory, "e2fs_stage1_5");
        extractAssetFileInDirectory(assetManager, directory, "fat_stage1_5");
        extractAssetFileInDirectory(assetManager, directory, "grub");
        extractAssetFileInDirectory(assetManager, directory, "ntfs_stage1_5");
        extractAssetFileInDirectory(assetManager, directory, "stage1");
        extractAssetFileInDirectory(assetManager, directory, "stage2");

        //chmod a+x /hd/grub
        suCommand("cp -R /sdcard/Download/grub/* /hd/grub/");
        //allow root to execute grub
        suCommand("chmod 700 /hd/grub/grub");
    }

    public void installBootloader() {
        //1. Run grub
        //1.1 find /boot/grub/stage1 (optional)
        //1.2 root (hdX,Y)
        //1.3 setup (hd0)
        //1.4 quit

    }

    private void createGrubMenu(){
        //Create menu.lst:
        //title Windows 95/98/NT/2000
        //root (hd0,0)
        //makeactive
        //chainloader +1

        //title Linux
        //root (hd0,1)
        //kernel /vmlinuz root=/dev/hda3 ro
    }

    private String suCommand(String command){
        String commandOutput = "";
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su","-c",command});
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                output.append(line + "\n");
            }

            commandOutput = output.toString();
        }catch (IOException exception){
            throw new RuntimeException("Failed to execute command '"+command+"' as root: "+exception.getMessage());
        }
        return commandOutput;
    }

    private void extractAssetFileInDirectory(AssetManager assetManager, File outputDirectory, String assetFilename){
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(assetFilename);
            File outFile = new File(outputDirectory, assetFilename);
            out = new FileOutputStream(outFile);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch(IOException exception) {
            throw new RuntimeException("Failed to copy asset file: " + assetFilename+". "+exception.getMessage());
            //Log.e("tag", "Failed to copy asset file: " + assetFilename, e);
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

}
