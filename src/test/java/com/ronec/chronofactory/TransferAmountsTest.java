package com.ronec.chronofactory;
import java.util.Arrays;
public final class TransferAmountsTest {
    static void expect(int count,int[] changes,int[] expected){
        int[] actual=TransferAmounts.allocate(count,changes);
        if(!Arrays.equals(actual,expected))throw new AssertionError(Arrays.toString(actual)+" != "+Arrays.toString(expected));
    }
    public static void main(String[] args){
        expect(1,new int[]{1},new int[]{1}); // five -> four
        expect(1,new int[]{5,-4},new int[]{1,0}); // pick up five, return four elsewhere
        expect(1,new int[]{4,-3},new int[]{1,0});
        expect(3,new int[]{1,0,2},new int[]{1,0,2});
        expect(0,new int[]{5,-5},new int[]{0,0});
        expect(1,new int[]{0,0,1},new int[]{0,0,1}); // destination slot
        expect(5,new int[]{0,2},new int[]{0,2}); // never invent a missing transfer
        System.out.println("Transfer quantity allocation: 7 regression cases passed.");
    }
}
