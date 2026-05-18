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

    private LocalDate virtualToday = LocalDate.now();

    @Override
    public void start(Stage primaryStage) {

        BorderPane root = new BorderPane();

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

        // ===== DATE PICKER =====
        DatePicker datePicker = new DatePicker();
        datePicker.setValue(virtualToday);
        datePicker.setPromptText("Дата");

        // запрет ручного редактирования текста
        datePicker.getEditor().setDisable(true);
        datePicker.getEditor().setOpacity(1);

        // запрет прошлых дат
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(virtualToday));
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

            // если дата в DatePicker уже прошла —
            // автоматически ставим текущую дату
            if (datePicker.getValue() == null ||
                    datePicker.getValue().isBefore(virtualToday)) {

                datePicker.setValue(virtualToday);
            }

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
                datePicker,
                new LabelStyled("Повтор"),
                repeatBox,
                addBtn,
                saveBtn,
                loadBtn
        );

        ScrollPane scroll = new ScrollPane(tasksContainer);
        scroll.setFitToWidth(true);

        root.setLeft(menu);
        root.setCenter(scroll);

        // ===== ADD TASK =====
        addBtn.setOnAction(e -> {

            String name = nameField.getText().trim();
            String desc = descField.getText().trim();
            LocalDate date = datePicker.getValue();

            // ===== VALIDATION =====
            if (name.isEmpty() || desc.isEmpty() || date == null) {
                showAlert("Ошибка", "Заполните название, описание и дату");
                return;
            }

            if (date.isBefore(virtualToday)) {
                showAlert("Ошибка", "Дата не может быть раньше сегодняшней");
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
            datePicker.setValue(virtualToday);
            repeatBox.setValue("Разовая");
        });

        saveBtn.setOnAction(e -> saveTasks());

        loadBtn.setOnAction(e -> {
            tasks.clear();
            tasksContainer.getChildren().clear();
            loadTasks();
        });

        loadTasks();

        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Список задач");
        primaryStage.show();
    }

    // ===== TASK CARD =====
    private void addTaskCard(TaskItem task) {

        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: white;-fx-background-radius: 12;");

        Label name = new Label(task.title);
        name.setStyle("-fx-font-size: 18px;-fx-font-weight: bold;");

        Label desc = new Label(task.description);
        desc.setWrapText(true);

        Label deadline = new Label("Срок: " + task.deadline.format(formatter));

        Label status = new Label(task.completed ? "выполнено" : "не выполнено");
        status.setTextFill(task.completed ? Color.GREEN : Color.RED);

        Button doneBtn = createSmallButton("Готово");
        Button deleteBtn = createSmallButton("Удалить");

        doneBtn.setOnAction(e -> {
            task.completed = true;
            redrawTasks();
        });

        deleteBtn.setOnAction(e -> {
            tasks.remove(task);
            redrawTasks();
        });

        card.getChildren().addAll(name, desc, deadline, status,
                new HBox(10, doneBtn, deleteBtn));

        tasksContainer.getChildren().add(card);
    }

    // ===== REFRESH =====
    private void redrawTasks() {
        tasksContainer.getChildren().clear();
        for (TaskItem t : tasks) addTaskCard(t);
    }

    // ===== SAVE =====
    private void saveTasks() {
        try (PrintWriter w = new PrintWriter(new FileWriter(saveFile))) {
            for (TaskItem t : tasks) {
                w.println(t.title + ";;" + t.description + ";;" + t.deadline + ";;" + t.repeatType);
            }
            showAlert("OK", "Сохранено");
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось сохранить");
        }
    }

    // ===== LOAD =====
    private void loadTasks() {
        if (!saveFile.exists()) return;

        try (BufferedReader r = new BufferedReader(new FileReader(saveFile))) {
            String line;
            while ((line = r.readLine()) != null) {

                String[] p = line.split(";;");
                if (p.length < 4) continue;

                TaskItem t = new TaskItem(
                        p[0],
                        p[1],
                        LocalDate.parse(p[2]),
                        p[3]
                );

                tasks.add(t);
            }
            redrawTasks();

        } catch (Exception e) {
            showAlert("Ошибка", "Load failed");
        }
    }

    private void updateTasksForNewDay() {
        redrawTasks();
    }

    private Button createButton(String t) {
        Button b = new Button(t);
        b.setPrefWidth(220);
        return b;
    }

    private Button createSmallButton(String t) {
        return new Button(t);
    }

    private void showAlert(String t, String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(t);
        a.setContentText(m);
        a.showAndWait();
    }

    private static class LabelStyled extends Label {
        LabelStyled(String t) {
            super(t);
            setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    private static class TaskItem {
        String title;
        String description;
        LocalDate deadline;
        String repeatType;
        boolean completed;

        TaskItem(String t, String d, LocalDate dl, String r) {
            title = t;
            description = d;
            deadline = dl;
            repeatType = r;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}