package com.dorm.ndidrs_back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dorm.ndidrs_back.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    SysUser login(String username, String password);
    boolean existsByUsername(String username);
}