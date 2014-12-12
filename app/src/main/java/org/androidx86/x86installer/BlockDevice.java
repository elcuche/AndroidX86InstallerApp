package org.androidx86.x86installer;

import java.math.BigDecimal;

public class BlockDevice {
    private final String deviceBlockName;
    private final BigDecimal sizeInGb;
    private boolean selected;

    public BlockDevice(String deviceBlockName, BigDecimal sizeInGb){
        this.deviceBlockName = deviceBlockName;
        this.sizeInGb = sizeInGb;
        this.selected = false;
    }

    public String getDeviceBlockName() {
        return deviceBlockName;
    }

    public BigDecimal getSizeInGb() {
        return sizeInGb;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }


    public static DiskPartition createFromBlockDeviceName(String deviceBlockName) {
        DiskPartition diskPartition = null;
        if (deviceBlockName != null && !deviceBlockName.isEmpty()){
            if (deviceBlockName.length() == 4){
                Disk disk = createDisk(deviceBlockName.substring(0, deviceBlockName.length()-1));
                if (disk != null) {
                    diskPartition = new DiskPartition(disk, deviceBlockName, BigDecimal.ZERO);
                    disk.addPartition(diskPartition);
                }
            }
        }
        return diskPartition;
    }

    public static Disk createDisk(String deviceBlockName) {
        if (deviceBlockName != null && !deviceBlockName.isEmpty()) {
            if (deviceBlockName.length() == 3) {
                return new Disk(deviceBlockName, BigDecimal.ZERO);
            }
        }
        return null;
    }


}
