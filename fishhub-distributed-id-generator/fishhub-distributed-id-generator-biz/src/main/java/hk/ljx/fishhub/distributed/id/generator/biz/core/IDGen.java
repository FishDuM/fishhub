package hk.ljx.fishhub.distributed.id.generator.biz.core;

public interface IDGen {
    hk.ljx.fishhub.distributed.id.generator.biz.core.common.Result get(String key);
    boolean init();
}
