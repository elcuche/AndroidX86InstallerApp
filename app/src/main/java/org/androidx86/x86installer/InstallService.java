package org.androidx86.x86installer;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InstallService {
    private final String cdromDevice = "/dev/block/sr0";

    public List<InstallMedia> listInstallMedias(File androidDownloadDir){
        List<InstallMedia> installMedias = new ArrayList<InstallMedia>();
        if (tryMountCdrom()){
            File file = new File(cdromDevice);
            InstallMedia installMedia = new InstallMedia("/dev/block/"+file.getName(),
                        "removable media",
                    R.drawable.ic_isofile);

            installMedias.add(installMedia);
        }
        addIsoFiles(installMedias, androidDownloadDir);
        return installMedias;
    }

    private void addIsoFiles(List<InstallMedia> installMedias, File parentDirectory) {
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
            InstallMedia installMedia = new InstallMedia(file.getName(),
                    parentDirectory.getPath(),
                    R.drawable.ic_isofile);

            installMedias.add(installMedia);
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
        suCommand("mkdir -p "+getCdrom());
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
        unmountInstallationMedia();
        String output = suCommand("mount -t iso9660 -r "+cdromDevice+" "+getCdrom());
        return output.isEmpty();
    }

    public void mountIsoFile(String isoFile){
        remountRootFsAsReadWrite();
        //mount -t iso9660 -o ro,loop /storage/sdcard0/Download/Android-x86-xx.iso
        unmountInstallationMedia();
        suCommand("losetup -d /dev/block/loop0", true);
        suCommand("mount -t iso9660 -o ro,loop "+isoFile+" "+getCdrom());
    }

    public void unmountInstallationMedia(){
        suCommand("umount "+getCdrom(), true);
    }

    private String getBlockDevice(String partition){
        return "/dev/block/"+partition;
    }

    public void mountPartition(String targetPartition) {
        final String targetBlockDevice = getBlockDevice(targetPartition);
        remountRootFsAsReadWrite();
        suCommand("mkdir -p /hd");
        unmountBlockDevice("/hd");
        mountBlockDevice(targetBlockDevice, "/hd");
    }

    public void unmountPartition(String targetPartition) {
        unmountBlockDevice("/hd");
    }

    public void installOnPartition(String releaseVersion, String targetPartition) {
       final String installDirectory = "/hd/"+releaseVersion;
       final String dataDirectory = installDirectory+"/data";
       suCommand("mkdir -p "+installDirectory);
       suCommand("mkdir -p "+dataDirectory);
       suCommand("cp "+getCdrom()+"/kernel"+" "+installDirectory);
       suCommand("cp "+getCdrom()+"/initrd.img"+" "+installDirectory);
       suCommand("cp "+getCdrom()+"/ramdisk.img"+" "+installDirectory);
       suCommand("cp "+getCdrom()+"/system.sfs"+" "+installDirectory);
    }

    public void expandSystemOnDisk(String releaseVersion) {
        final String installDirectory = "/hd/"+releaseVersion;
        final String systemDirectory = installDirectory+"/system";
        final String tmpDirectory = "/hd/tmp";
        suCommand("mkdir -p "+systemDirectory);
        suCommand("mkdir -p "+tmpDirectory);
        //losetup -f
        //losetup -d /dev/block/loop0
        suCommand("losetup -d /dev/block/loop0", true);
        //mount -t squashfs -o,loop /hd/system.sfs /hd/tmp
        suCommand("mount -t squashfs -o,loop "+installDirectory+"/system.sfs "+tmpDirectory);
        suCommand("cp "+tmpDirectory+"/system.img "+installDirectory);
        suCommand("umount "+tmpDirectory);
        suCommand("losetup -d /dev/block/loop0", true);

        suCommand("mount -t ext4 -o,loop "+installDirectory+"/system.img /hd/tmp");
        suCommand("cp -R /hd/tmp/* "+systemDirectory);
        suCommand("umount "+tmpDirectory);
        suCommand("losetup -d /dev/block/loop0", true);

        suCommand("rm -Rf "+tmpDirectory);
        suCommand("rm -f "+installDirectory+"/system.sfs");
        suCommand("rm -f "+installDirectory+"/system.img");
    }

    private void unmountBlockDevice(String directory) {
        //umount /hd
        suCommand("umount "+directory, true);
    }

    private void mountBlockDevice(String targetBlockDevice, String directory) {
        //mount -t ext2 targetBlockDevice /hd
        suCommand("mount -t ext2 "+targetBlockDevice+" "+directory);
    }

    public void formatPartition(String targetPartition) {
        final String targetBlockDevice = getBlockDevice(targetPartition);
        //fdisk: 'n' creates new partitions, 'd' delete partition, 'p' print partitions, 'q' quit, 'w' write and quit.
        //suCommand("echo -e \"p\\nq\\n\" | fdisk "+targetBlockDevice);
        //ext2: make2fs -L Name /dev/block/sda1
        //ext3: make2fs -jL Name /dev/block/sda1
        //ntfs: mkntfs -fL
        //fat32: newfs_msdos -L
        //exfat?
        //btrfs:?
        suCommand("mke2fs -L AndroidX86 "+targetBlockDevice);
    }

    public void installGrubFiles(AssetManager assetManager, File downloadDirectory, String targetPartition) {
        //mkdir /hd/grub
        suCommand("mkdir -p /hd/grub");
        //touch /hd/grub/menu.lst
        suCommand("touch /hd/grub/menu.lst");

        //cp assets/* /hd/
        File directory = new File(downloadDirectory+File.separator+"grub");
        directory.mkdir();
        extractAssetFileInDirectory(assetManager, directory, "e2fs_stage1_5");
        extractAssetFileInDirectory(assetManager, directory, "fat_stage1_5");
        extractAssetFileInDirectory(assetManager, directory, "ntfs_stage1_5");
        extractAssetFileInDirectory(assetManager, directory, "stage1");
        extractAssetFileInDirectory(assetManager, directory, "stage2");
        //grub don't works as it need libc6 (would need as bionic build of grub...)
        //extractAssetFileInDirectory(assetManager, directory, "grub");

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
        return suCommand(command, false);
    }

    private String suCommand(String command, boolean ignoreError){
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su","-c",command});
            BufferedReader inputStreamReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            BufferedReader errorStreamReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = inputStreamReader.readLine()) != null) {
                output.append(line + "\n");
            }

            String errorLine;
            while ((errorLine = errorStreamReader.readLine()) != null) {
                error.append(errorLine + "\n");
            }
        }catch (IOException exception){
            throw new RuntimeException("Failed to execute command '"+command+"' as root: "+exception.getMessage());
        }
        if (!ignoreError && error.length() > 0){
            throw new RuntimeException("Failed to execute command '"+command+"' as root. Error: "+error.toString());
        }
        return output.toString();
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
