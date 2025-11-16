package kosukeroku.steam.library.analyzer.telegram;

import kosukeroku.steam.library.analyzer.service.BotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LibraryAnalyzerBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final BotService botService;

    public LibraryAnalyzerBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            BotService botService) {
        super(botToken);
        this.botUsername = botUsername;
        this.botService = botService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update); // processing text messages
            } else if (update.hasCallbackQuery()) {
                handleButtonClick(update); // processing button clicks
            }
        } catch (Exception e) {
            log.error("Error processing update: {}", e.getMessage(), e);
        }
    }

    private void handleTextMessage(Update update) throws TelegramApiException {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        String response = botService.handleInitialMessage(messageText, chatId);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(response);
        message.setParseMode("Markdown");

        // showing buttons after a reply which has a message asking for a next query
        if (response.contains("What would you like to know?")) {
            message.setReplyMarkup(createMainMenuKeyboard());
        }

        execute(message);
    }

    private void handleButtonClick(Update update) throws TelegramApiException {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        // message to send before starting calculations—subject to remove later if a way to make processing near-instant is found
            SendMessage waitMessage = new SendMessage();
            waitMessage.setChatId(String.valueOf(chatId));
            waitMessage.setText("⏳ *Loading data, please wait...*");
            waitMessage.setParseMode("Markdown");
            execute(waitMessage);


        String response = botService.handleButtonResponse(callbackData, chatId);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(response);
        message.setParseMode("Markdown");

        message.setReplyMarkup(createMainMenuKeyboard());

        execute(message);
    }

    private InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // first row: overall stats
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("🎮 Top Games")
                .callbackData("top_games")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("🏆 Achievements")
                .callbackData("achievements")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("👥 Friends Stats")
                .callbackData("friends")
                .build());


        rows.add(row1);
        markup.setKeyboard(rows);

        return markup;
    }
}