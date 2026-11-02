package com.expeditions.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FieldEquipmentTest {
    @Test void corruptEnergyIsBounded(){assertEquals(0,FieldEquipment.stored(-10));assertEquals(16000,FieldEquipment.stored(Integer.MAX_VALUE));}
    @Test void transfersCannotCreateCharge(){assertEquals(0,FieldEquipment.transfer(0,1000,false));assertEquals(0,FieldEquipment.transfer(16000,1000,true));assertEquals(3,FieldEquipment.transfer(15997,1000,true));assertEquals(4,FieldEquipment.transfer(4,1000,false));}
    @Test void negativeAndHugeRequestsAreSafe(){assertEquals(0,FieldEquipment.transfer(8000,-1,true));assertEquals(1000,FieldEquipment.transfer(8000,Integer.MAX_VALUE,false));}
    @Test void fullCellFundsFourCampsOrEightyReadings(){assertEquals(4,FieldEquipment.CELL_CAPACITY/FieldEquipment.WARD_COST);assertEquals(80,FieldEquipment.CELL_CAPACITY/FieldEquipment.READING_COST);assertEquals(120,FieldEquipment.WARD_TICKS/20);}
}
