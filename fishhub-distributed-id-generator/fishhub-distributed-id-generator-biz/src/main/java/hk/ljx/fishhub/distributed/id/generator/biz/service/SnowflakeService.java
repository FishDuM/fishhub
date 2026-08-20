package hk.ljx.fishhub.distributed.id.generator.biz.service;

import hk.ljx.fishhub.distributed.id.generator.biz.constant.Constants;
import hk.ljx.fishhub.distributed.id.generator.biz.core.IDGen;
import hk.ljx.fishhub.distributed.id.generator.biz.core.common.PropertyFactory;
import hk.ljx.fishhub.distributed.id.generator.biz.core.common.Result;
import hk.ljx.fishhub.distributed.id.generator.biz.core.common.ZeroIDGen;
import hk.ljx.fishhub.distributed.id.generator.biz.core.snowflake.SnowflakeIDGenImpl;
import hk.ljx.fishhub.distributed.id.generator.biz.exception.InitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service("SnowflakeService")
public class SnowflakeService {
    private Logger logger = LoggerFactory.getLogger(SnowflakeService.class);

    private IDGen idGen;

    public SnowflakeService() throws InitException {
        Properties properties = PropertyFactory.getProperties();
        boolean flag = Boolean.parseBoolean(properties.getProperty(Constants.LEAF_SNOWFLAKE_ENABLE, "true"));
        if (flag) {
            try {
                String zkAddress = properties.getProperty(Constants.LEAF_SNOWFLAKE_ZK_ADDRESS);
                int port = Integer.parseInt(properties.getProperty(Constants.LEAF_SNOWFLAKE_PORT));
                SnowflakeIDGenImpl snowflakeIDGen = new SnowflakeIDGenImpl(zkAddress, port);
                if (snowflakeIDGen.init()) {
                    idGen = snowflakeIDGen;
                    logger.info("Snowflake Service Init Successfully");
                } else {
                    logger.error("Snowflake Service Init Fail, fallback to ZeroIDGen");
                    idGen = new ZeroIDGen();
                }
            } catch (Exception e) {
                logger.error("Snowflake Service Init Exception, fallback to ZeroIDGen", e);
                idGen = new ZeroIDGen();
            }
        } else {
            idGen = new ZeroIDGen();
            logger.info("Zero ID Gen Service Init Successfully");
        }
    }

    public Result getId(String key) {
        return idGen.get(key);
    }
}
