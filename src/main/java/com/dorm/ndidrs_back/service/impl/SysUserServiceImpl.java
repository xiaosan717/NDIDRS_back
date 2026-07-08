package com.dorm.ndidrs_back.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.mapper.SysUserMapper;
import com.dorm.ndidrs_back.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    @Override
    public SysUser login(String username, String password) {
        return this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getPassword, password)
                .eq(SysUser::getStatus, 1));
    }

    @Override
    public boolean existsByUsername(String username) {
        return this.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0;
    }
}