package com.example.security.member;

import com.example.security.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void username_으로_회원을_찾는다() {
        memberRepository.save(Member.create("bob", "{noop}pw", "밥", Role.USER));

        assertThat(memberRepository.findByUsername("bob"))
                .get()
                .extracting(Member::getNickname, Member::getRole)
                .containsExactly("밥", Role.USER);
        assertThat(memberRepository.existsByUsername("bob")).isTrue();
        assertThat(memberRepository.existsByUsername("nobody")).isFalse();
    }

    @Test
    void username_은_유니크_제약이_걸려_있다() {
        memberRepository.saveAndFlush(Member.create("carol", "{noop}pw", "캐롤", Role.USER));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(Member.create("carol", "{noop}pw", "캐롤2", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
