package org.androidx86.x86installer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Disk extends BlockDevice {
    private List<DiskPartition> partitions = new ArrayList<DiskPartition>();

    public Disk(String deviceBlockName, BigDecimal sizeInGb){
        super(deviceBlockName, sizeInGb);
        this.partitions = partitions;
    }

    public DiskPartition[] getPartitions(){
        return partitions.toArray(new DiskPartition[]{});
    }

    void addPartition(DiskPartition partition){
        if (partition != null) {
            partitions.add(partition);
        }
    }

    public static boolean isDisk(String deviceBlockName){
        return deviceBlockName != null && deviceBlockName.length() == 3;
    }
}
