package com.photoshare.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/team-members")
public class TeamMemberController {
    private final MemberService members;

    public TeamMemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    EventDtos.UserListResponse list() {
        return members.roster();
    }
}

