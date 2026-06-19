package com.example.movierecommendation.controller;

import com.example.movierecommendation.entity.AIChatLog;
import com.example.movierecommendation.entity.User;
import com.example.movierecommendation.repository.AIChatLogRepository;
import com.example.movierecommendation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/chat-logs")
public class AdminChatLogController {

    @Autowired
    private AIChatLogRepository aiChatLogRepository;

    @Autowired
    private UserService userService;

    private void addCurrentUser(UserDetails ud, Model model) {
        if (ud != null) {
            User u = userService.getCurrentUser(ud.getUsername());
            model.addAttribute("currentUser", u);
        }
    }

    @GetMapping
    public String listChatLogs(@RequestParam(name = "page", defaultValue = "0") int page,
                               @AuthenticationPrincipal UserDetails ud,
                               Model model) {
        addCurrentUser(ud, model);
        Page<AIChatLog> chatLogPage = aiChatLogRepository.findAllWithUser(PageRequest.of(page, 20));
        model.addAttribute("chatLogPage", chatLogPage);
        return "admin/chat-logs";
    }

    @GetMapping("/{id}/details")
    public String getChatLogDetails(@PathVariable("id") Integer id, Model model) {
        AIChatLog chatLog = aiChatLogRepository.findByIdWithRecommendations(id)
                .orElseThrow(() -> new IllegalArgumentException("Chat log not found with ID: " + id));
        model.addAttribute("chatLog", chatLog);
        return "admin/chat-logs :: detail-modal-content";
    }

    @PostMapping("/{id}/delete")
    public String deleteChatLog(@PathVariable("id") Integer id, RedirectAttributes redirect) {
        try {
            aiChatLogRepository.deleteById(id);
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("success", isVi ? "Xóa nhật ký chat thành công." : "Chat log deleted successfully.");
        } catch (Exception e) {
            boolean isVi = "vi".equalsIgnoreCase(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage());
            redirect.addFlashAttribute("error", isVi ? "Lỗi khi xóa nhật ký chat: " + e.getMessage() : "Error deleting chat log: " + e.getMessage());
        }
        return "redirect:/admin/chat-logs";
    }
}
