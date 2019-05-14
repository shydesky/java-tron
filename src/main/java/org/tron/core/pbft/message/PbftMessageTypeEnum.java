package org.tron.core.pbft.message;

public enum PbftMessageTypeEnum {
  CV(1, "视图变更"),
  REQ(2, "请求数据"),
  PP(3, "预准备阶段"),
  PA(4, "准备阶段"),
  CM(5, "提交阶段");

  PbftMessageTypeEnum(int code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public static PbftMessageTypeEnum getEnum(int code) {
    for (PbftMessageTypeEnum typeEnum : values()) {
      if (typeEnum.code == code) {
        return typeEnum;
      }
    }
    return null;
  }

  private int code;
  private String desc;

  public int getCode() {
    return code;
  }

  public PbftMessageTypeEnum setCode(int code) {
    this.code = code;
    return this;
  }

  public String getDesc() {
    return desc;
  }

  public PbftMessageTypeEnum setDesc(String desc) {
    this.desc = desc;
    return this;
  }
}
