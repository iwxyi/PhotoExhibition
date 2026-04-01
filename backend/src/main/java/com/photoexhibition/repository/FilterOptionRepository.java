package com.photoexhibition.repository;

import com.photoexhibition.entity.FilterOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FilterOptionRepository extends JpaRepository<FilterOption, Long> {

    List<FilterOption> findByOptionType(String optionType);

    List<FilterOption> findByOptionTypeAndUserId(String optionType, Long userId);

    Optional<FilterOption> findByOptionTypeAndOptionKey(String optionType, String optionKey);

    Optional<FilterOption> findByOptionTypeAndOptionKeyAndUserId(String optionType, String optionKey, Long userId);

    @Modifying
    @Query("DELETE FROM FilterOption WHERE optionType = :optionType")
    void deleteByOptionType(@Param("optionType") String optionType);

    @Modifying
    @Query("DELETE FROM FilterOption WHERE optionType = :optionType AND ((:userId IS NULL AND userId IS NULL) OR userId = :userId)")
    void deleteByOptionTypeAndUserId(@Param("optionType") String optionType, @Param("userId") Long userId);

    @Query("SELECT fo FROM FilterOption fo WHERE fo.optionType = :optionType ORDER BY fo.optionKey")
    List<FilterOption> findByOptionTypeOrderByOptionKey(@Param("optionType") String optionType);

    @Query("SELECT fo FROM FilterOption fo WHERE fo.optionType = :optionType AND ((:userId IS NULL AND fo.userId IS NULL) OR fo.userId = :userId) ORDER BY fo.optionKey")
    List<FilterOption> findByOptionTypeAndUserIdOrderByOptionKey(@Param("optionType") String optionType, @Param("userId") Long userId);
}
