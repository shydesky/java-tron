package org.tron.core.zen.address;

import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.zksnark.Librustzcash;
import org.tron.common.zksnark.LibrustzcashParam.IvkToPkdParams;
import org.tron.core.exception.ZksnarkException;

// ivk
@Slf4j(topic = "shieldTransaction")
@AllArgsConstructor
public class IncomingViewingKey {

  @Setter
  @Getter
  public byte[] value; // 256

  public Optional<PaymentAddress> address(DiversifierT d) throws ZksnarkException {
    System.out.println("test----------------value:" + Arrays.toString(value));
    byte[] pkD = new byte[32]; // 32
    byte[] dd = new byte[]{
        0,0,0,0,0,0,0,0,0,0,0
    };
    byte[] res = new byte[]{
        85, -19, 83, -16, -42, 85, 11, 71, 44, -33, 56, -26, 12, 2, -53, 4, 42, -52, -60, -90, 61, 16, 7, -100, 75, 127, -16, -41, 106, -17, -97, 7
    };
    System.out.println("test--------------dd:" + Librustzcash.librustzcashCheckDiversifier(dd));
    if (Librustzcash.librustzcashCheckDiversifier(dd)) {
      if (!Librustzcash.librustzcashIvkToPkd(new IvkToPkdParams(
          res,
          dd, pkD))) {
//        throw new ZksnarkException("librustzcashIvkToPkd error");
      }
      return Optional.of(new PaymentAddress(d, pkD));
    } else {
      return Optional.empty();
    }
  }
}
