package hk.ljx.fishhub.auth.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDO {
    private Long id;

    private String username;

    private Date createTime;

    private Date updateTime;
}