package com.dorm.ndidrs_back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("dorm_check_record")
public class DormCheckRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private LocalDate checkDate;
    private Long studentId;
    private String status;
    private String remark;
    private String image;
    private Long submitterId;
    private LocalDateTime submitTime;
    private Long modifierId;
    private LocalDateTime modifyTime;
    private String modifyRemark;
    private Integer isModified;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDate getCheckDate() { return checkDate; }
    public void setCheckDate(LocalDate checkDate) { this.checkDate = checkDate; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public Long getModifierId() { return modifierId; }
    public void setModifierId(Long modifierId) { this.modifierId = modifierId; }
    public LocalDateTime getModifyTime() { return modifyTime; }
    public void setModifyTime(LocalDateTime modifyTime) { this.modifyTime = modifyTime; }
    public String getModifyRemark() { return modifyRemark; }
    public void setModifyRemark(String modifyRemark) { this.modifyRemark = modifyRemark; }
    public Integer getIsModified() { return isModified; }
    public void setIsModified(Integer isModified) { this.isModified = isModified; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
