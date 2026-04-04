package com.example.do_an_ck_J2EE.repository;

import com.example.do_an_ck_J2EE.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySenderAndReceiverOrderByCreatedAtAsc(String sender, String receiver);

    long countByReceiverAndReadFalse(String receiver);

    long countBySenderAndReceiverAndReadFalse(String sender, String receiver);

    @Query("""
        select distinct
            case
                when c.sender = :adminUsername then c.receiver
                else c.sender
            end
        from ChatMessage c
        where c.sender = :adminUsername or c.receiver = :adminUsername
        order by
            case
                when c.sender = :adminUsername then c.receiver
                else c.sender
            end asc
    """)
    List<String> findAllChatUsers(@Param("adminUsername") String adminUsername);

    @Query("""
        select c.sender
        from ChatMessage c
        where c.receiver = :receiver
          and c.read = false
        group by c.sender
        order by max(c.createdAt) desc
    """)
    List<String> findUnreadSendersByReceiver(@Param("receiver") String receiver);

    @Query("""
        select count(c)
        from ChatMessage c
        where c.sender = :sender
          and c.receiver = :receiver
          and c.read = false
    """)
    long countUnreadBySenderAndReceiver(@Param("sender") String sender,
                                        @Param("receiver") String receiver);
}