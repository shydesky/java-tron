package org.tron.core.consensus;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.db.Manager;
import org.tron.core.db.VotesStore;
import org.tron.core.db.WitnessStore;
import org.tron.core.store.AccountStore;

@Slf4j(topic = "consensus")
@Component
public class ConsensusDelegate {

  @Autowired
  private Manager manager;

  public int calculateFilledSlotsCount() {
    return manager.getDynamicPropertiesStore().calculateFilledSlotsCount();
  }

  public void saveRemoveThePowerOfTheGr(long rate) {
    manager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(rate);
  }

  public long getRemoveThePowerOfTheGr() {
    return manager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr();
  }

  public long getWitnessStandbyAllowance() {
    return  manager.getDynamicPropertiesStore().getWitnessStandbyAllowance();
  }

  public BlockCapsule getGenesisBlock() {
    return manager.getGenesisBlock();
  }

  public long getLatestBlockHeaderTimestamp() {
    return manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
  }

  public long getLatestBlockHeaderNumber() {
    return manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();
  }

  public boolean lastHeadBlockIsMaintenance() {
    return manager.lastHeadBlockIsMaintenance();
  }

  public long getSkipSlotInMaintenance() {
    return manager.getSkipSlotInMaintenance();
  }

  public WitnessCapsule getWitnesseByAddress(ByteString address) {
    return this.manager.getWitnessStore().get(address.toByteArray());
  }

  public void saveActiveWitnesses(List<ByteString> addresses) {
    this.manager.getWitnessScheduleStore().saveActiveWitnesses(addresses);
  }

  public List<ByteString> getActiveWitnesses() {
    return this.manager.getWitnessScheduleStore().getActiveWitnesses();
  }

  public WitnessStore getWitnessStore() {
    return manager.getWitnessStore();
  }

  public VotesStore getVotesStore() {
    return manager.getVotesStore();
  }

  public AccountStore getAccountStore() {
    return manager.getAccountStore();
  }

  public void saveStateFlag(int flag) {
    manager.getDynamicPropertiesStore().saveStateFlag(flag);
  }

  public int saveStateFlag() {
    return manager.getDynamicPropertiesStore().getStateFlag();
  }

  public long getMaintenanceTimeInterval() {
    return manager.getDynamicPropertiesStore().getMaintenanceTimeInterval();
  }

  public void saveMaintenanceTimeInterval(long time) {
    manager.getDynamicPropertiesStore().saveMaintenanceTimeInterval(time);
  }

  public long getNextMaintenanceTime() {
    return manager.getDynamicPropertiesStore().getNextMaintenanceTime();
  }

  public void saveNextMaintenanceTime(long time) {
    manager.getDynamicPropertiesStore().saveNextMaintenanceTime(time);
  }

  public long getWitnessPayPerBlock() {
    return manager.getDynamicPropertiesStore().getWitnessPayPerBlock();
  }

  public long getLatestSolidifiedBlockNum() {
    return manager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
  }

  public void saveLatestSolidifiedBlockNum(long num) {
    manager.getDynamicPropertiesStore().saveLatestSolidifiedBlockNum(num);
  }

  public int[] getBlockFilledSlots() {
    return manager.getDynamicPropertiesStore().getBlockFilledSlots();
  }

  public void saveBlockFilledSlots(int[] slots) {
    manager.getDynamicPropertiesStore().saveBlockFilledSlots(slots);
  }

  public int getBlockFilledSlotsIndex() {
    return manager.getDynamicPropertiesStore().getBlockFilledSlotsIndex();
  }

  public void saveBlockFilledSlotsIndex(int index) {
    manager.getDynamicPropertiesStore().saveBlockFilledSlotsIndex(index);
  }

  public int getBlockFilledSlotsNumber() {
    return manager.getDynamicPropertiesStore().getBlockFilledSlotsNumber();
  }

  public Sha256Hash getLatestBlockHeaderHash() {
    return manager.getDynamicPropertiesStore().getLatestBlockHeaderHash();
  }

  public long getAllowMultiSign() {
    return manager.getDynamicPropertiesStore().getAllowMultiSign();
  }

}
