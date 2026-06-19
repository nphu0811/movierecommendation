package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.Comment;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.service.InteractionService;
import com.example.movierecommendation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/comments")
public class AdminCommentController {

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private UserService userService;

    private void addCurrentUser(UserDetails ud, Model model) {
        if (ud != null) {
            User u = userService.getCurrentUser(ud.getUsername());
            model.addAttribute("currentUser", u);
        }
    }

    @GetMapping
    public String listComments(@RequestParam(name = "page", defaultValue = "0") int page,
                               @AuthenticationPrincipal UserDetails ud,
                               Model model) {
        addCurrentUser(ud, model);
        Page<Comment> commentPage = interactionService.getAllCommentsPaged(page, 20);
        model.addAttribute("commentPage", commentPage);
        return "admin/comments";
    }

    @PostMapping("/{id}/hide")
    public String hideComment(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        try {
            interactionService.softDeleteComment(id);
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi ? "Ẩn bình luận thành công." : "Comment hidden successfully.");
        } catch (Exception e) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("error", (isVi ? "Lỗi: " : "Error: ") + e.getMessage());
        }
        return "redirect:/admin/comments";
    }

    @PostMapping("/{id}/restore")
    public String restoreComment(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        try {
            interactionService.restoreComment(id);
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi ? "Khôi phục bình luận thành công." : "Comment restored successfully.");
        } catch (Exception e) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("error", (isVi ? "Lỗi: " : "Error: ") + e.getMessage());
        }
        return "redirect:/admin/comments";
    }
}
