package com.ronec.chronofactory;
/** Allocation of the actual net transfer, not the size of the source stack. */
final class TransferAmounts {
    private TransferAmounts(){}
    static int[] allocate(int requested,int[] changes){
        int[] result=new int[changes.length];int left=Math.max(0,requested);
        for(int i=0;i<changes.length&&left>0;i++){int n=Math.min(left,Math.max(0,changes[i]));result[i]=n;left-=n;}
        return result;
    }
}
