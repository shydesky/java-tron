package org.tron.common.zksnark;

public class LibrustzcashJni {
  static {
    System.load(Librustzcash.getLibraryByName("libhello"));
  }

  private native boolean librustzcashCheckDiversifier(byte[] d);

  public static void main(String[] args) {
    new LibrustzcashJni().librustzcashCheckDiversifier(new byte[1]);
  }

  public static class LibrustzcashWraper {
    public static boolean run(byte[] d) {
      return new LibrustzcashJni().librustzcashCheckDiversifier(d);
    }
  }
}
