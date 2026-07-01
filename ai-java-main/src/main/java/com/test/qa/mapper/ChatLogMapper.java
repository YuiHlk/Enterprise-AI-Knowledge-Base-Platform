package com.test.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.qa.domain.ChatLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatLogMapper extends BaseMapper<ChatLog> {
}
