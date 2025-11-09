package kosukeroku.steam.library.analyzer.telegram;


import kosukeroku.steam.library.analyzer.service.BotService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class LibraryAnalyzerBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final BotService telegramBotService;

    public LibraryAnalyzerBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            BotService telegramBotService) {
        super(botToken);  // передаем токен в родительский класс
        this.botUsername = botUsername;
        this.telegramBotService = telegramBotService;
    }


    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @SneakyThrows
    @Override

    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            String response = telegramBotService.handleMessage(messageText, chatId);

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(response);

            message.setParseMode("Markdown");

            execute(message);
        }
    }
}