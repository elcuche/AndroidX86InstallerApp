package org.androidx86.x86installer;

import java.math.BigDecimal;

public class DiskPartition extends BlockDevice {
    private final transient Disk disk;

    public DiskPartition(Disk disk, String deviceBlockName, BigDecimal sizeInGb) {
        super(deviceBlockName, sizeInGb);
        this.disk = disk;
    }

    public Disk getDisk() {
        return disk;
    }

    public String getGrub2BiosMBRLabel() {
        char diskNumberChar = getDeviceBlockName().toLowerCase().charAt("sd".length());
        //ASCII for a=97
        int diskNumber = Character.isDigit(diskNumberChar) ? Character.digit(diskNumberChar, 10) : ((int) diskNumberChar) - 96;
        int partitionNumber = Character.digit(getDeviceBlockName().charAt(getDeviceBlockName().length() - 1), 10);
        //MBR partition table: hd0,msdos1
        //GPT partition table: hd0,gpt1
        return "hd" + (diskNumber-1) + ",msdos" + partitionNumber;
    }

    public static boolean isDiskPartition(String deviceBlockName){
        return deviceBlockName != null && deviceBlockName.length() == 4;
    }
}
