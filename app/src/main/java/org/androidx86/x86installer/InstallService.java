package org.androidx86.x86installer;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
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
        //or use 'parted /dev/block/sda print'?
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
        tryUnmountInstallationMedia();
        String output = suCommand("mount -t iso9660 -r "+cdromDevice+" "+getCdrom());
        return output.isEmpty();
    }

    public void mountIsoFile(String isoFile){
        remountRootFsAsReadWrite();
        //mount -t iso9660 -o ro,loop /storage/sdcard0/Download/Android-x86-xx.iso
        tryUnmountInstallationMedia();
        suCommand("losetup -d /dev/block/loop3", true);
        //suCommand("mount -t iso9660 -o ro,loop "+isoFile+" "+getCdrom());

        //suCommand("mount -t iso9660 -o ro,loop=/dev/block "+isoFile+" "+getCdrom());
        suCommand("losetup /dev/block/loop3 "+isoFile, true);
        suCommand("mount -t iso9660 -o ro /dev/block/loop3 "+getCdrom());
    }

    public void tryUnmountInstallationMedia(){
        suCommand("umount "+getCdrom(), true);
    }

    public void unmountInstallationMedia(){
        suCommand("umount "+getCdrom(), false);
        suCommand("losetup -d /dev/block/loop3 ", true);
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
        suCommand("umount "+tmpDirectory, true);
        //suCommand("losetup -d /dev/block/loop0", true);
        //suCommand("losetup -d /dev/block/loop1", true);
        suCommand("losetup /dev/block/loop2 "+installDirectory+"/system.sfs", true);
        //mount -t squashfs -o,loop /hd/system.sfs /hd/tmp
        suCommand("mount -t squashfs /dev/block/loop2 "+tmpDirectory);
        suCommand("cp "+tmpDirectory+"/system.img "+installDirectory);
        suCommand("umount "+tmpDirectory);
        suCommand("losetup -d /dev/block/loop2", true);

        suCommand("losetup /dev/block/loop2 "+installDirectory+"/system.img", true);
        suCommand("mount -t ext4 /dev/block/loop2 "+tmpDirectory);
        suCommand("cp -R "+tmpDirectory+"/* "+systemDirectory);
        suCommand("umount "+tmpDirectory);
        suCommand("losetup -d /dev/block/loop2", true);

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

    public void formatPartition(DiskPartition targetPartition) {
        final String targetBlockDevice = "/dev/block/"+targetPartition.getDeviceBlockName();
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

    /**
     * @see 'http://en.wikipedia.org/wiki/GNU_GRUB'
     */
    public void copyGrub2FilesOnPartition(String androidDirectory, AssetManager assetManager, File downloadDirectory, DiskPartition targetPartition) {
        //mkdir /hd/grub
        suCommand("mkdir -p /hd/boot/grub");
        suCommand("mkdir -p /hd/boot/grub/i386-pc");

        //cp assets/* /hd/
        File directory = new File(downloadDirectory+File.separator+"grub2");
        directory.mkdir();
        extractAssetFileInDirectory(assetManager, directory, "boot.img");
        extractAssetFileInDirectory(assetManager, directory, "bios-mbr-core.img");
        //Copy .mod into /boot/grub/i386-pc/
        extractAssetFileInDirectory(assetManager, directory, "boot.mod");
        extractAssetFileInDirectory(assetManager, directory, "bufio.mod");
        extractAssetFileInDirectory(assetManager, directory, "crypto.mod");
        extractAssetFileInDirectory(assetManager, directory, "extcmd.mod");
        extractAssetFileInDirectory(assetManager, directory, "gettext.mod");
        extractAssetFileInDirectory(assetManager, directory, "normal.mod");
        extractAssetFileInDirectory(assetManager, directory, "terminal.mod");

        //Create menu in /boot/grub/grub.cfg
        createGrub2Menu(directory, targetPartition, androidDirectory);

        suCommand("cp -R /sdcard/Download/grub2/*.img /hd/boot/grub/");
        suCommand("cp -R /sdcard/Download/grub2/*.cfg /hd/boot/grub/");
        suCommand("cp -R /sdcard/Download/grub2/*.mod /hd/boot/grub/i386-pc");
        suCommand("mv /hd/boot/grub/bios-mbr-core.img /hd/boot/grub/core.img");
    }


    public void installBootloader(DiskPartition diskPartition) {
        installGrub2OnMBRDisk("/dev/block/"+diskPartition.getDisk().getDeviceBlockName());
    }

    /**
     * see http://pete.akeo.ie/2014/05/compiling-and-installing-grub2-for.html
     * to generate custom bios-mbr-core.img that boots other things than ntfs, exfat, btrfs, ext2.
     *
     * I used for 'Bios-MBR-Table' bios-mbr-core.img:
     * ../grub-mkimage -v -O i386-pc -d. -p\(hd0,msdos1\)/boot/grub biosdisk part_msdos fat ntfs exfat ext2 btrfs -o bios-mbr-core.img
     */
    private void installGrub2OnMBRDisk(final String blockDevice){
        //copy boot.img on MBR (make sure that only the first 446 bytes of boot.img are copied, so as not to overwrites the partition data that also resides in the MBR and that has already been filled)
        //dd if=boot.img of=/dev/block/sdb bs=446 count=1
        suCommand("dd if=/hd/boot/grub/boot.img of="+blockDevice+" bs=446 count=1", true);
        //copy bios-mbr-core.img after partition table
        //dd if=bios-mbr-core.img of=/dev/block/sdb bs=512 seek=1 # seek=1 skips the first block (MBR)
        suCommand("dd if=/hd/boot/grub/core.img of="+blockDevice+" bs=512 seek=1", true);
    }

    /**
     * https://wiki.archlinux.org/index.php/GRUB
     */
    private void installGrub2OnGPTDisk(){
        //1. find the ESP
        //1.1 create ESP if missing
        //2.
    }

    private void createGrub2Menu(File directory, DiskPartition targetPartition, String androidDirectory){
        try {
            File grub2ConfFile = new File(directory, "grub.cfg");
            if (!grub2ConfFile.exists()){
                grub2ConfFile.createNewFile();
            }
            FileWriter grub2Conf = new FileWriter(grub2ConfFile);
            //grub2Conf.append("insmod echo\n");
            grub2Conf.append("set timeout = 5\n");

            //grub2Conf.append("menuentry \"test\" {\n");
            //grub2Conf.append("  echo \"hello\"\n");
            //grub2Conf.append("}\n");


            grub2Conf.append("menuentry \"Android-x86\" {\n");
            grub2Conf.append("  set root=("+targetPartition.getGrub2BiosMBRLabel()+")\n");
            grub2Conf.append("  linux /"+androidDirectory+"/kernel quiet androidboot.hardware=android_x86 video=-16 SRC=/"+androidDirectory+"\n");
            grub2Conf.append("  initrd /"+androidDirectory+"/initrd.img"+"\n");
            grub2Conf.append("}\n");
            grub2Conf.close();
        } catch (IOException exception){
            throw new RuntimeException(exception);
        }
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
