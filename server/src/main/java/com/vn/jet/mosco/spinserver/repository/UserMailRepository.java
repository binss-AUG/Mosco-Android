package com.vn.jet.mosco.spinserver.repository;

import com.vn.jet.mosco.spinserver.model.User;
import com.vn.jet.mosco.spinserver.model.UserMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserMailRepository extends JpaRepository<UserMail, Long> {
    List<UserMail> findByUserId(Long userId);
    void deleteByUser(User user);
}
