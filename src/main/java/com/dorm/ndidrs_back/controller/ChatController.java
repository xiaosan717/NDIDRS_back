package com.dorm.ndidrs_back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dorm.ndidrs_back.common.JwtUtils;
import com.dorm.ndidrs_back.common.Result;
import com.dorm.ndidrs_back.entity.ChatMessage;
import com.dorm.ndidrs_back.entity.DormRoom;
import com.dorm.ndidrs_back.entity.SysUser;
import com.dorm.ndidrs_back.service.ChatMessageService;
import com.dorm.ndidrs_back.service.DormRoomService;
import com.dorm.ndidrs_back.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final List<String> ALLOWED_ROLES = List.of("STUDENT", "DORM_LEADER");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;   // 5MB
    private static final long MAX_VIDEO_SIZE = 20 * 1024 * 1024;  // 20MB

    private final ChatMessageService chatMessageService;
    private final SysUserService userService;
    private final DormRoomService dormRoomService;
    private final JwtUtils jwtUtils;

    @Value("${app.upload-dir:uploads/chat}")
    private String uploadDir;

    public ChatController(ChatMessageService chatMessageService,
                          SysUserService userService,
                          DormRoomService dormRoomService,
                          JwtUtils jwtUtils) {
        this.chatMessageService = chatMessageService;
        this.userService = userService;
        this.dormRoomService = dormRoomService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 获取当前用户的宿舍群聊房间信息
     */
    @GetMapping("/my-room")
    public Result<Map<String, Object>> getMyRoom(HttpServletRequest request) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null || role == null) {
            return Result.error(401, "登录状态已失效");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return Result.error(403, "当前角色无权使用宿舍聊天，角色=" + role);
        }

        try {
            SysUser user = userService.getById(userId);
            if (user == null) {
                return Result.error(400, "用户不存在，userId=" + userId);
            }
            if (user.getBuilding() == null || user.getRoom() == null) {
                return Result.error(400, "您未绑定宿舍信息（building=" + user.getBuilding() + ", room=" + user.getRoom() + "）");
            }

            // 查找对应宿舍（false 表示多条结果时取第一条，不抛异常）
            DormRoom room = dormRoomService.getOne(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getBuilding, user.getBuilding())
                    .eq(DormRoom::getRoomNumber, user.getRoom()), false);
            if (room == null) {
                return Result.error(404, "未找到对应宿舍（楼栋：" + user.getBuilding() + "，房号：" + user.getRoom() + "）");
            }

            // 获取群成员
            List<SysUser> members = userService.list(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getBuilding, user.getBuilding())
                    .eq(SysUser::getRoom, user.getRoom())
                    .in(SysUser::getRole, ALLOWED_ROLES)
                    .eq(SysUser::getStatus, 1));

            List<Map<String, Object>> memberList = new ArrayList<>();
            for (SysUser m : members) {
                Map<String, Object> member = new LinkedHashMap<>();
                member.put("id", m.getId());
                member.put("realName", m.getRealName() != null ? m.getRealName() : m.getUsername());
                member.put("avatar", m.getAvatar());
                member.put("role", m.getRole());
                memberList.add(member);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("roomId", room.getId());
            result.put("roomName", room.getBuilding() + " " + room.getRoomNumber());
            result.put("members", memberList);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "[DEBUG] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 获取聊天历史消息
     */
    @GetMapping("/{roomId}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable String roomId,
                                                  @RequestParam(required = false) Long beforeId,
                                                  @RequestParam(defaultValue = "50") int limit,
                                                  HttpServletRequest request) {
        Long userId = jwtUtils.getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "登录状态已失效");
        }
        List<ChatMessage> messages = chatMessageService.getHistory(roomId, beforeId, limit);
        return Result.success(messages);
    }

    /**
     * 发送文本消息（REST方式，WebSocket也可发送）
     */
    @PostMapping("/{roomId}/send")
    public Result<ChatMessage> sendMessage(@PathVariable String roomId,
                                            @RequestBody Map<String, String> body,
                                            HttpServletRequest request) {
        Long userId = jwtUtils.getCurrentUserId(request);
        String role = jwtUtils.getCurrentUserRole(request);
        if (userId == null || role == null) {
            return Result.error(401, "登录状态已失效");
        }

        SysUser user = userService.getById(userId);
        if (user == null) {
            return Result.error(401, "用户不存在");
        }

        String content = body.get("content");
        String msgType = body.getOrDefault("type", "TEXT");
        if (content == null || content.isBlank()) {
            return Result.error(400, "消息内容不能为空");
        }

        String senderName = user.getRealName() != null ? user.getRealName() : user.getUsername();
        ChatMessage saved = chatMessageService.saveMessage(roomId, userId, senderName, user.getAvatar(), msgType, content);
        return Result.success(saved);
    }

    /**
     * 上传图片
     */
    @PostMapping("/upload/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Long userId = jwtUtils.getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "登录状态已失效");
        }
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return Result.error(400, "图片大小不能超过5MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName);
        if (!List.of("jpg", "jpeg", "png", "gif", "webp", "bmp").contains(ext.toLowerCase())) {
            return Result.error(400, "不支持的图片格式");
        }

        try {
            String url = saveFile(file, "images", ext);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("type", "IMAGE");
            return Result.success(result);
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传视频
     */
    @PostMapping("/upload/video")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Long userId = jwtUtils.getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "登录状态已失效");
        }
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            return Result.error(400, "视频大小不能超过20MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName);
        if (!List.of("mp4", "webm", "mov").contains(ext.toLowerCase())) {
            return Result.error(400, "不支持的视频格式");
        }

        try {
            String url = saveFile(file, "videos", ext);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("type", "VIDEO");
            return Result.success(result);
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    private String saveFile(MultipartFile file, String subDir, String ext) throws IOException {
        String filename = UUID.randomUUID() + "." + ext.toLowerCase();
        // 必须使用绝对路径，否则 transferTo() 会相对于 Tomcat 临时目录解析
        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path filePath = dir.resolve(filename);
        file.transferTo(filePath.toFile());
        return "/api/chat/media/" + subDir + "/" + filename;
    }

    /**
     * 获取媒体文件（图片/视频）
     */
    @GetMapping("/media/{subDir}/{filename}")
    public void getMedia(@PathVariable String subDir, @PathVariable String filename,
                         jakarta.servlet.http.HttpServletResponse response) throws IOException {
        if (!List.of("images", "videos").contains(subDir)) {
            response.sendError(404);
            return;
        }
        Path filePath = Paths.get(uploadDir, subDir, filename).toAbsolutePath().normalize();
        File file = filePath.toFile();
        if (!file.exists()) {
            response.sendError(404);
            return;
        }
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";
        response.setContentType(contentType);
        response.setContentLengthLong(file.length());
        Files.copy(filePath, response.getOutputStream());
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
