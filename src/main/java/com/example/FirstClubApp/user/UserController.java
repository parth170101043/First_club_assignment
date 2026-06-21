package com.example.FirstClubApp.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes REST operations for creating, retrieving, and deleting FirstClub users.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    /**
     * Creates the user REST controller.
     *
     * @param userService service that owns user validation and persistence
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Creates a user from a validated JSON request.
     *
     * @param request user details parsed from the request body
     * @return created user response with HTTP 201
     * @implNote Used by API clients before authentication and subscriptions are created.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserDtos.Response create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return userService.create(request);
    }

    /**
     * Lists every registered user.
     *
     * @return registered users; defaults to an empty list
     * @implNote Used by development and future administrative clients.
     */
    @GetMapping
    List<UserDtos.Response> findAll() {
        return userService.findAll();
    }

    /**
     * Retrieves one user by UUID.
     *
     * @param id UUID supplied in the URL path
     * @return matching user response
     * @implNote Used by development and future administrative clients.
     */
    @GetMapping("/{id}")
    UserDtos.Response findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    /**
     * Deletes a user who has no subscription history.
     *
     * @param id UUID supplied in the URL path
     * @return no response body with HTTP 204
     * @implNote Used by API clients to remove temporary users created during testing.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
