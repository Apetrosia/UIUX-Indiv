package org.example;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {

    private final List<TaskItem> tasks = new ArrayList<>();
    private final VBox tasksContainer = new VBox(10);

    private final File saveFile = new File("tasks.txt");

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

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

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Срок");

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

        menu.getChildren().addAll(
                title,
                new LabelStyled("Название"),
                nameField,

                new LabelStyled("Описание"),
                descField,

                new LabelStyled("Срок"),
                deadlinePicker,

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
            LocalDate date = deadlinePicker.getValue();

            if (name.isEmpty() || date == null) {
                showAlert("Ошибка", "Введите название и срок.");
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
            deadlinePicker.setValue(null);
            repeatBox.setValue("Разовая");
        });

        saveBtn.setOnAction(e -> saveTasks());

        loadBtn.setOnAction(e -> {
            tasks.clear();
            tasksContainer.getChildren().clear();
            loadTasks();
        });

        loadTasks();

        Scene scene = new Scene(root, 1000, 650);

        primaryStage.setTitle("Список задач");
        primaryStage.setScene(scene);
        primaryStage.show();
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
                showAlert("Информация", "Задача уже выполнена.");
                return;
            }

            if (LocalDate.now().isAfter(task.deadline)) {

                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Комментарий");
                dialog.setHeaderText("Задача выполнена не в срок");
                dialog.setContentText("Введите причину:");

                dialog.showAndWait().ifPresent(comment -> {
                    task.comment = comment;
                });
            }

            task.completed = true;

            status.setText("Статус: выполнено");
            status.setTextFill(Color.GREEN);

            if (!task.comment.isEmpty()) {
                Label commentLabel =
                        new Label("Комментарий: " + task.comment);

                commentLabel.setStyle("-fx-text-fill: #7f8c8d;-fx-font-style: italic;");

                if (!card.getChildren().contains(commentLabel)) {
                    card.getChildren().add(commentLabel);
                }
            }
        });

        // ===== CHANGE DEADLINE =====

        editDeadlineBtn.setOnAction(e -> {

            Dialog<LocalDate> dialog = new Dialog<>();
            dialog.setTitle("Изменение срока");

            DatePicker picker = new DatePicker(task.deadline);
            TextArea reasonArea = new TextArea();
            reasonArea.setPromptText("Причина изменения срока");

            VBox content = new VBox(10,
                    new Label("Новый срок"),
                    picker,
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
                if (btn == ok) return picker.getValue();
                return null;
            });

            dialog.showAndWait().ifPresent(newDate -> {

                if (reasonArea.getText().trim().isEmpty()) {
                    showAlert("Ошибка",
                            "Нужно указать причину изменения срока.");
                    return;
                }

                task.deadline = newDate;
                task.changeReason = reasonArea.getText();

                deadline.setText(
                        "Срок: " + task.deadline.format(formatter)
                );

                Label reasonLabel =
                        new Label("Причина переноса: "
                                + task.changeReason);

                reasonLabel.setStyle("-fx-text-fill: #8e44ad;-fx-font-style: italic;");

                boolean exists = false;

                for (var node : card.getChildren()) {
                    if (node instanceof Label &&
                            ((Label) node).getText().startsWith("Причина переноса")) {
                        Label l = (Label) node;
                        l.setText(reasonLabel.getText());
                        exists = true;
                    }
                }

                if (!exists) {
                    card.getChildren().add(reasonLabel);
                }
            });
        });

        // ===== DELETE =====

        deleteBtn.setOnAction(e -> {
            tasks.remove(task);
            tasksContainer.getChildren().remove(card);
        });

        card.getChildren().addAll(
                name,
                desc,
                deadline,
                repeat,
                status,
                controls
        );

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
                                t.changeReason
                );
            }

            showAlert("Успех",
                    "Задачи сохранены в файл.");

        } catch (IOException e) {
            showAlert("Ошибка",
                    "Не удалось сохранить файл.");
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

                if (parts.length < 7) continue;

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

                tasks.add(task);
                addTaskCard(task);
            }

        } catch (IOException e) {
            showAlert("Ошибка",
                    "Не удалось загрузить файл.");
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

        TaskItem(String title,
                 String description,
                 LocalDate deadline,
                 String repeatType) {

            this.title = title;
            this.description = description;
            this.deadline = deadline;
            this.repeatType = repeatType;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}