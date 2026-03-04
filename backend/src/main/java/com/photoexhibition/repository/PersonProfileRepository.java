package com.photoexhibition.repository;

import com.photoexhibition.entity.PersonProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonProfileRepository extends JpaRepository<PersonProfile, Long> {

    Optional<PersonProfile> findByName(String name);

    /**
     * 按人脸数量倒序分页查询人物
     */
    @Query("SELECT p FROM PersonProfile p LEFT JOIN p.faces f GROUP BY p ORDER BY COUNT(f) DESC")
    Page<PersonProfile> findAllOrderByFaceCountDesc(Pageable pageable);

    /**
     * 按人脸数量倒序分页查询可见人物（排除 hidden=true）
     */
    @Query("SELECT p FROM PersonProfile p LEFT JOIN p.faces f WHERE p.hidden = false OR p.hidden IS NULL GROUP BY p ORDER BY COUNT(f) DESC")
    Page<PersonProfile> findVisibleOrderByFaceCountDesc(Pageable pageable);

    /**
     * 根据名称模糊搜索人物（用于短链接）
     */
    @Query("SELECT p FROM PersonProfile p WHERE p.name LIKE %:name% ORDER BY SIZE(p.faces) DESC")
    Optional<PersonProfile> searchByName(@Param("name") String name);

    /**
     * 根据名称模糊搜索人物列表（返回多个结果用于匹配）
     */
    @Query("SELECT p FROM PersonProfile p WHERE p.name LIKE %:name% ORDER BY SIZE(p.faces) DESC")
    java.util.List<PersonProfile> searchByNameList(@Param("name") String name);
}

