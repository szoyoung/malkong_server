package com.example.ddorang.presentation.repository;

import com.example.ddorang.presentation.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    
    // 특정 프레젠테이션의 모든 댓글 조회 (시간순 정렬)
    @Query("SELECT c FROM Comment c WHERE c.presentation.id = :presentationId AND c.parentComment IS NULL ORDER BY c.timestamp ASC NULLS LAST")
    List<Comment> findByPresentationIdOrderByTimestamp(@Param("presentationId") UUID presentationId);
    
    // 특정 프레젠테이션의 모든 댓글 조회 (작성일순 정렬 - 최신순)
    @Query("SELECT c FROM Comment c WHERE c.presentation.id = :presentationId AND c.parentComment IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findByPresentationIdOrderByCreatedAt(@Param("presentationId") UUID presentationId);
    
    // 특정 댓글의 대댓글 조회
    @Query("SELECT c FROM Comment c WHERE c.parentComment.id = :parentCommentId ORDER BY c.createdAt ASC")
    List<Comment> findRepliesByParentCommentId(@Param("parentCommentId") UUID parentCommentId);
    
    // 특정 사용자의 댓글 조회
    @Query("SELECT c FROM Comment c WHERE c.user.userId = :userId ORDER BY c.createdAt DESC")
    List<Comment> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);
    
    // 특정 프레젠테이션의 댓글 개수 조회
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.presentation.id = :presentationId")
    long countByPresentationId(@Param("presentationId") UUID presentationId);
    
    // 특정 댓글의 대댓글 개수 조회
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.parentComment.id = :parentCommentId")
    long countRepliesByParentCommentId(@Param("parentCommentId") UUID parentCommentId);
    
    // 사용자가 특정 프레젠테이션에 작성한 댓글 조회
    @Query("SELECT c FROM Comment c WHERE c.presentation.id = :presentationId AND c.user.userId = :userId ORDER BY c.timestamp ASC NULLS LAST")
    List<Comment> findByPresentationIdAndUserId(@Param("presentationId") UUID presentationId, @Param("userId") UUID userId);
    
    // 댓글 내용으로 검색
    @Query("SELECT c FROM Comment c WHERE c.presentation.id = :presentationId AND LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.timestamp ASC NULLS LAST")
    List<Comment> searchCommentsByKeyword(@Param("presentationId") UUID presentationId, @Param("keyword") String keyword);
    
    // 댓글 작성자 확인 (권한 검증용)
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Comment c WHERE c.id = :commentId AND c.user.userId = :userId")
    boolean existsByIdAndUserId(@Param("commentId") UUID commentId, @Param("userId") UUID userId);
    
}