package com.hdfs.transfer.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdfs.transfer.server.entity.SysUserEntity;
import com.hdfs.transfer.server.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SysUserService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities("USER")
                .build();
    }

    public SysUserEntity getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public SysUserEntity getById(Long id) {
        return userMapper.selectById(id);
    }

    public boolean register(SysUserEntity user) {
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.insert(user) > 0;
    }

    public boolean updateProfile(String currentUsername, String newUsername, String realName, String email, String phone) {
        SysUserEntity user = userMapper.selectByUsername(currentUsername);
        if (user == null) {
            return false;
        }
        if (newUsername != null && !newUsername.equals(currentUsername)) {
            SysUserEntity exist = userMapper.selectByUsername(newUsername);
            if (exist != null) {
                return false;
            }
            user.setUsername(newUsername);
        }
        if (realName != null) user.setRealName(realName);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.updateById(user) > 0;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        SysUserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            return false;
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.updateById(user) > 0;
    }

    public SysUserEntity getProfile(String username) {
        SysUserEntity user = userMapper.selectByUsername(username);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    public Page<SysUserEntity> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUserEntity::getUsername, keyword)
                    .or().like(SysUserEntity::getRealName, keyword)
                    .or().like(SysUserEntity::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUserEntity::getCreateTime);
        Page<SysUserEntity> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        for (SysUserEntity u : page.getRecords()) {
            u.setPassword(null);
        }
        return page;
    }

    public void addUser(SysUserEntity user) {
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new RuntimeException("用户名已存在: " + user.getUsername());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        user.setRole("user");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    public void updateUser(SysUserEntity user) {
        SysUserEntity existing = userMapper.selectById(user.getId());
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getRealName() != null) existing.setRealName(user.getRealName());
        if (user.getEmail() != null) existing.setEmail(user.getEmail());
        if (user.getPhone() != null) existing.setPhone(user.getPhone());
        existing.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(existing);
    }

    public void updateUserStatus(Long id, int status) {
        SysUserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void resetPassword(Long id, String newPassword) {
        SysUserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
}