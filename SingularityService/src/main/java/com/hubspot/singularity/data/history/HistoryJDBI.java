package com.hubspot.singularity.data.history;

import java.util.Date;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import com.hubspot.singularity.SingularityRequestHistory;
import com.hubspot.singularity.SingularityTaskHistoryUpdate;
import com.hubspot.singularity.SingularityTaskIdHistory;
import com.hubspot.singularity.data.history.SingularityTaskHistoryHelper.SingularityTaskHistoryHelperMapper;
import com.hubspot.singularity.data.history.mappers.SingularityRequestHistoryMapper;
import com.hubspot.singularity.data.history.mappers.SingularityTaskIdHistoryMapper;
import com.hubspot.singularity.data.history.mappers.SingularityTaskUpdateMapper;

public interface HistoryJDBI {

  @SqlUpdate("INSERT INTO requestHistory (requestId, request, createdAt, requestState, user) VALUES (:requestId, :request, :createdAt, :requestState, :user)")
  void insertRequestHistory(@Bind("requestId") String requestId, @Bind("request") byte[] request, @Bind("createdAt") Date createdAt, @Bind("requestState") String requestState, @Bind("user") String user);
  
  @SqlUpdate("INSERT INTO taskHistory (requestId, taskId, task, status, createdAt) VALUES (:requestId, :taskId, :task, :status, :createdAt)")
  void insertTaskHistory(@Bind("requestId") String requestId, @Bind("taskId") String taskId, @Bind("task") byte[] task, @Bind("status") String status, @Bind("createdAt") Date createdAt);

  @SqlUpdate("INSERT INTO taskUpdates (taskId, status, message, createdAt) VALUES (:taskId, :status, :message, :createdAt)")
  void insertTaskUpdate(@Bind("taskId") String taskId, @Bind("status") String status, @Bind("message") String message, @Bind("createdAt") Date createdAt);
  
  @SqlUpdate("UPDATE taskHistory SET lastTaskStatus = :lastStatus, updatedAt = :updatedAt WHERE taskId = :taskId")
  void updateTaskStatus(@Bind("taskId") String taskId, @Bind("lastStatus") String status, @Bind("updatedAt") Date updatedAt);
  
  @Mapper(SingularityTaskUpdateMapper.class)
  @SqlQuery("SELECT status, message, createdAt FROM taskUpdates WHERE taskId = :taskId")
  List<SingularityTaskHistoryUpdate> getTaskUpdates(@Bind("taskId") String taskId);
  
  @Mapper(SingularityTaskHistoryHelperMapper.class)
  @SqlQuery("SELECT createdAt, task FROM taskHistory WHERE taskId = :taskId")
  SingularityTaskHistoryHelper getTaskHistoryForTask(@Bind("taskId") String taskId);
  
  @Mapper(SingularityTaskIdHistoryMapper.class)
  @SqlQuery("SELECT taskId, createdAt, updatedAt, lastTaskStatus FROM taskHistory WHERE requestId = :requestId")
  List<SingularityTaskIdHistory> getTaskHistoryForRequest(@Bind("requestId") String requestId);
  
  @Mapper(SingularityTaskIdHistoryMapper.class)
  @SqlQuery("SELECT taskId, createdAt, updatedAt, lastTaskStatus FROM taskHistory WHERE requestId LIKE CONCAT('%', CONCAT(:requestIdLike, '%'))")
  List<SingularityTaskIdHistory> getTaskHistoryForRequestLike(@Bind("requestIdLike") String requestIdLike);
  
  @Mapper(SingularityRequestHistoryMapper.class)
  @SqlQuery("SELECT request, createdAt, requestState, user FROM requestHistory WHERE requestId = :requestId")
  List<SingularityRequestHistory> getRequestHistory(@Bind("requestId") String requestId);
  
  @Mapper(SingularityRequestHistoryMapper.class)
  @SqlQuery("SELECT request, createdAt, requestState, user FROM requestHistory WHERE requestId LIKE CONCAT('%', CONCAT(:requestIdLike, '%'))")
  List<SingularityRequestHistory> getRequestHistoryLike(@Bind("requestIdLike") String requestIdLike);
  
  void close();

  
}
