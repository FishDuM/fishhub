package hk.ljx.fishhub.distributed.id.generator.biz.core;

import hk.ljx.fishhub.distributed.id.generator.biz.core.common.Result;

public interface IDGen {
    Result get(String key);
    boolean init();
}
