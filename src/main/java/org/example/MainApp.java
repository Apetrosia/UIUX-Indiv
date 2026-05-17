package org.example;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class MainApp extends Application {

    private final List<TaskItem> tasks = new ArrayList<>();
    private final VBox tasksContainer = new VBox(10);

    private final File saveFile = new File("tasks.txt");

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ===== ЧИТ ДЛЯ ДЕМОНСТРАЦИИ =====
    private LocalDate virtualToday = LocalDate.now();

    @Override
    public void start(Stage primaryStage) {

        BorderPane root = new BorderPane();

        // ===== LEFT MENU =====

        VBox menu = new VBox(12);
        menu.setPadding(new Insets(15));
        menu.setPrefWidth(280);
        menu.setAlignment(Pos.TOP_CENTER);

        menu.setStyle("-fx-background-color: linear-gradient(to bottom, #2c3e50, #34495e);");

        Label title = new Label("Список задач");
        title.setStyle("-fx-text-fill: white;-fx-font-size: 22px;-fx-font-weight: bold;");

        TextField nameField = new TextField();
        nameField.setPromptText("Название задачи");

        TextArea descField = new TextArea();
        descField.setPromptText("Описание");
        descField.setPrefRowCount(4);

        // ===== DATE FIELD =====

        TextField dateField = new TextField();
        dateField.setPromptText("ДД.ММ.ГГГГ");

        UnaryOperator<TextFormatter.Change> filter = change -> {

            String newText = change.getControlNewText();

            // только цифры и точки
            if (!newText.matches("[0-9.]*")) {
                return null;
            }

            // максимум 10 символов
            if (newText.length() > 10) {
                return null;
            }

            return change;
        };

        dateField.setTextFormatter(new TextFormatter<>(filter));

        // автоточки
        dateField.textProperty().addListener((obs, oldText, newText) -> {

            if (newText.length() == 2 && !newText.contains(".")) {
                dateField.setText(newText + ".");
            }

            if (newText.length() == 5 &&
                    newText.chars().filter(ch -> ch == '.').count() == 1) {
                dateField.setText(newText + ".");
            }
        });

        ComboBox<String> repeatBox = new ComboBox<>(
                FXCollections.observableArrayList(
                        "Разовая",
                        "Ежедневно",
                        "Еженедельно",
                        "Ежемесячно"
                )
        );

        repeatBox.setValue("Разовая");

        Button addBtn = createButton("Добавить задачу");
        Button saveBtn = createButton("Сохранить");
        Button loadBtn = createButton("Загрузить");

        // ===== ЧИТ-КНОПКА =====

        Button nextDayBtn = createButton("+ день");

        Label currentDateLabel = new Label(
                "Сегодня: " + virtualToday.format(formatter)
        );

        currentDateLabel.setStyle("-fx-text-fill: white;-fx-font-size: 14px;");

        nextDayBtn.setOnAction(e -> {

            virtualToday = virtualToday.plusDays(1);

            currentDateLabel.setText(
                    "Сегодня: " + virtualToday.format(formatter)
            );

            updateTasksForNewDay();
        });

        menu.getChildren().addAll(
                title,

                currentDateLabel,
                nextDayBtn,

                new LabelStyled("Название"),
                nameField,

                new LabelStyled("Описание"),
                descField,

                new LabelStyled("Срок"),
                dateField,

                new LabelStyled("Повтор"),
                repeatBox,

                addBtn,
                saveBtn,
                loadBtn
        );

        // ===== CENTER =====

        ScrollPane scroll = new ScrollPane(tasksContainer);
        scroll.setFitToWidth(true);

        tasksContainer.setPadding(new Insets(15));
        tasksContainer.setStyle("-fx-background-color: #ecf0f1;");

        root.setLeft(menu);
        root.setCenter(scroll);

        // ===== ACTIONS =====

        addBtn.setOnAction(e -> {

            String name = nameField.getText().trim();
            String desc = descField.getText().trim();
            String dateText = dateField.getText().trim();

            if (name.isEmpty() || dateText.length() != 10) {

                showAlert(
                        "Ошибка",
                        "Введите название и дату в формате ДД.ММ.ГГГГ"
                );

                return;
            }

            LocalDate date;

            try {
                date = LocalDate.parse(dateText, formatter);
            } catch (Exception ex) {

                showAlert(
                        "Ошибка",
                        "Некорректная дата."
                );

                return;
            }

            TaskItem task = new TaskItem(
                    name,
                    desc,
                    date,
                    repeatBox.getValue()
            );

            tasks.add(task);

            addTaskCard(task);

            nameField.clear();
            descField.clear();
            dateField.clear();

            repeatBox.setValue("Разовая");
        });

        saveBtn.setOnAction(e -> saveTasks());

        loadBtn.setOnAction(e -> {
            tasks.clear();
            tasksContainer.getChildren().clear();
            loadTasks();
        });

        loadTasks();

        Scene scene = new Scene(root, 1050, 700);

        primaryStage.setTitle("Список задач");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ===== ОБНОВЛЕНИЕ НОВОГО ДНЯ =====

    private void updateTasksForNewDay() {

        for (TaskItem task : tasks) {

            if (!task.repeatType.equals("Разовая")) {

                if (task.completed &&
                        virtualToday.isAfter(task.deadline)) {

                    // сбрасываем выполненность
                    task.completed = false;

                    // двигаем дедлайн вперед
                    switch (task.repeatType) {

                        case "Ежедневно":

                            while (task.deadline.isBefore(virtualToday)) {
                                task.deadline = task.deadline.plusDays(1);
                            }

                            break;

                        case "Еженедельно":

                            while (task.deadline.isBefore(virtualToday)) {
                                task.deadline = task.deadline.plusWeeks(1);
                            }

                            break;

                        case "Ежемесячно":

                            while (task.deadline.isBefore(virtualToday)) {
                                task.deadline =
                                        moveMonthlyDeadline(
                                                task.deadline,
                                                task.preferredDay
                                        );
                            }

                            break;
                    }
                }
            }
        }

        redrawTasks();
    }

    // ===== ПЕРЕРИСОВКА =====

    private void redrawTasks() {

        tasksContainer.getChildren().clear();

        for (TaskItem task : tasks) {
            addTaskCard(task);
        }
    }

    // ===== TASK CARD =====

    private void addTaskCard(TaskItem task) {

        VBox card = new VBox(8);
        card.setPadding(new Insets(12));

        card.setStyle("-fx-background-color: white;-fx-background-radius: 12;-fx-border-radius: 12;-fx-border-color: #dcdde1;");

        Label name = new Label(task.title);
        name.setStyle("-fx-font-size: 18px;-fx-font-weight: bold;");

        Label desc = new Label(task.description);
        desc.setWrapText(true);

        Label deadline = new Label(
                "Срок: " + task.deadline.format(formatter)
        );

        Label repeat = new Label(
                "Тип: " + task.repeatType
        );

        Label status = new Label(
                task.completed
                        ? "Статус: выполнено"
                        : "Статус: не выполнено"
        );

        if (task.completed) {
            status.setTextFill(Color.GREEN);
        } else {
            status.setTextFill(Color.DARKRED);
        }

        Label lastCompletedLabel = new Label();

        if (task.lastCompletedDate != null) {

            lastCompletedLabel.setText(
                    "Последнее выполнение: "
                            + task.lastCompletedDate.format(formatter)
            );

            lastCompletedLabel.setStyle(
                    "-fx-text-fill: #2980b9;"
            );
        }

        Button doneBtn = createSmallButton("Выполнено");
        Button editDeadlineBtn = createSmallButton("Изменить срок");
        Button deleteBtn = createSmallButton("Удалить");

        HBox controls = new HBox(10,
                doneBtn,
                editDeadlineBtn,
                deleteBtn
        );

        controls.setAlignment(Pos.CENTER_LEFT);

        // ===== COMPLETE =====

        doneBtn.setOnAction(e -> {

            if (task.completed) {

                showAlert(
                        "Информация",
                        "Задача уже выполнена сегодня."
                );

                return;
            }

            if (virtualToday.isAfter(task.deadline)) {

                TextInputDialog dialog = new TextInputDialog();

                dialog.setTitle("Комментарий");

                dialog.setHeaderText(
                        "Задача выполнена не в срок"
                );

                dialog.setContentText("Введите причину:");

                dialog.showAndWait().ifPresent(comment -> {
                    task.comment = comment;
                });
            }

            task.completed = true;
            task.lastCompletedDate = virtualToday;

            // ===== ЦИКЛИЧЕСКИЕ ЗАДАЧИ =====

            redrawTasks();
        });

        // ===== CHANGE DEADLINE =====

        editDeadlineBtn.setOnAction(e -> {

            Dialog<LocalDate> dialog = new Dialog<>();
            dialog.setTitle("Изменение срока");

            TextField newDateField = new TextField(
                    task.deadline.format(formatter)
            );

            TextArea reasonArea = new TextArea();
            reasonArea.setPromptText("Причина изменения срока");

            VBox content = new VBox(10,
                    new Label("Новый срок"),
                    newDateField,
                    new Label("Причина"),
                    reasonArea
            );

            dialog.getDialogPane().setContent(content);

            ButtonType ok =
                    new ButtonType("Сохранить",
                            ButtonBar.ButtonData.OK_DONE);

            dialog.getDialogPane().getButtonTypes().addAll(
                    ok,
                    ButtonType.CANCEL
            );

            dialog.setResultConverter(btn -> {

                if (btn == ok) {

                    try {
                        return LocalDate.parse(
                                newDateField.getText(),
                                formatter
                        );
                    } catch (Exception ex) {
                        return null;
                    }
                }

                return null;
            });

            dialog.showAndWait().ifPresent(newDate -> {

                if (reasonArea.getText().trim().isEmpty()) {

                    showAlert(
                            "Ошибка",
                            "Нужно указать причину изменения срока."
                    );

                    return;
                }

                task.deadline = newDate;
                task.changeReason = reasonArea.getText();

                redrawTasks();
            });
        });

        // ===== DELETE =====

        deleteBtn.setOnAction(e -> {

            tasks.remove(task);

            redrawTasks();
        });

        card.getChildren().addAll(
                name,
                desc,
                deadline,
                repeat,
                status
        );

        if (!lastCompletedLabel.getText().isEmpty()) {
            card.getChildren().add(lastCompletedLabel);
        }

        if (!task.comment.isEmpty()) {

            Label commentLabel =
                    new Label("Комментарий: " + task.comment);

            commentLabel.setStyle(
                    "-fx-text-fill: #7f8c8d;-fx-font-style: italic;"
            );

            card.getChildren().add(commentLabel);
        }

        if (!task.changeReason.isEmpty()) {

            Label reasonLabel =
                    new Label("Причина переноса: "
                            + task.changeReason);

            reasonLabel.setStyle(
                    "-fx-text-fill: #8e44ad;-fx-font-style: italic;"
            );

            card.getChildren().add(reasonLabel);
        }

        card.getChildren().add(controls);

        tasksContainer.getChildren().add(card);
    }

    // ===== SAVE =====

    private void saveTasks() {

        try (PrintWriter writer =
                     new PrintWriter(new FileWriter(saveFile))) {

            for (TaskItem t : tasks) {

                writer.println(
                        t.title + ";;" +
                                t.description + ";;" +
                                t.deadline + ";;" +
                                t.repeatType + ";;" +
                                t.completed + ";;" +
                                t.comment + ";;" +
                                t.changeReason + ";;" +
                                t.preferredDay + ";;" +
                                (t.lastCompletedDate == null
                                        ? "null"
                                        : t.lastCompletedDate)
                );
            }

            showAlert(
                    "Успех",
                    "Задачи сохранены в файл."
            );

        } catch (IOException e) {

            showAlert(
                    "Ошибка",
                    "Не удалось сохранить файл."
            );
        }
    }

    // ===== LOAD =====

    private void loadTasks() {

        if (!saveFile.exists()) return;

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(saveFile))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(";;");

                if (parts.length < 9) continue;

                TaskItem task = new TaskItem(
                        parts[0],
                        parts[1],
                        LocalDate.parse(parts[2]),
                        parts[3]
                );

                task.completed =
                        Boolean.parseBoolean(parts[4]);

                task.comment = parts[5];
                task.changeReason = parts[6];

                if (!parts[7].equals("null")) {
                    task.lastCompletedDate =
                            LocalDate.parse(parts[7]);
                }

                task.preferredDay = Integer.parseInt(parts[8]);

                tasks.add(task);
            }

            redrawTasks();

        } catch (IOException e) {

            showAlert(
                    "Ошибка",
                    "Не удалось загрузить файл."
            );
        }
    }

    // ===== BUTTONS =====

    private Button createButton(String text) {

        Button b = new Button(text);

        b.setPrefWidth(220);

        b.setStyle("-fx-background-color: #ecf0f1;-fx-background-radius: 8;-fx-font-size: 14px;");

        return b;
    }

    private Button createSmallButton(String text) {

        Button b = new Button(text);

        b.setStyle("-fx-background-color: #dfe6e9;-fx-background-radius: 8;");

        return b;
    }

    // ===== ALERT =====

    private void showAlert(String title, String text) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(text);

        alert.showAndWait();
    }

    // ===== LABEL =====

    private static class LabelStyled extends Label {

        LabelStyled(String text) {

            super(text);

            setStyle("-fx-text-fill: white;-fx-font-weight: bold;");
        }
    }

    // ===== TASK MODEL =====

    private static class TaskItem {

        String title;
        String description;

        LocalDate deadline;

        String repeatType;

        boolean completed = false;

        String comment = "";

        String changeReason = "";

        LocalDate lastCompletedDate = null;

        int preferredDay;

        TaskItem(String title,
                 String description,
                 LocalDate deadline,
                 String repeatType) {

            this.title = title;
            this.description = description;
            this.deadline = deadline;
            this.repeatType = repeatType;
            this.preferredDay = deadline.getDayOfMonth();
        }
    }

    private LocalDate moveMonthlyDeadline(LocalDate current, int preferredDay) {

        LocalDate nextMonth = current.plusMonths(1);

        int maxDay = nextMonth.lengthOfMonth();

        int targetDay = Math.min(preferredDay, maxDay);

        return nextMonth.withDayOfMonth(targetDay);
    }

    public static void main(String[] args) {
        launch();
    }
}