package com.swiftlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swiftlink.entity.ShortLink;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    @Select("SELECT id, short_code, origin_url, business_domain, expire_time, status, create_time, update_time "
            + "FROM t_short_link WHERE short_code = #{shortCode} LIMIT 1")
    ShortLink selectByShortCode(@Param("shortCode") String shortCode);
}
