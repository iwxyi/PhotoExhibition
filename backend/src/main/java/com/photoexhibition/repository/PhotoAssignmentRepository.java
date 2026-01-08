package com.photoexhibition.repository;

import com.photoexhibition.entity.PhotoAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoAssignmentRepository extends JpaRepository<PhotoAssignment, Long> {

    Optional<PhotoAssignment> findByPhotoId(Long photoId);

    Page<PhotoAssignment> findByPersonId(Long personId, Pageable pageable);

    List<PhotoAssignment> findByPersonId(Long personId);

    void deleteByPhotoId(Long photoId);
}


