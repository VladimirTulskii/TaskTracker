package filemanager;

import manager.InMemoryTaskManager;

import tasks.Epic;
import tasks.Status;
import tasks.Subtask;
import tasks.Task;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class FileBackedTasksManager extends InMemoryTaskManager {

    protected Map<Integer, Task> allTasks = new HashMap<>();

    private final static String HEAD = "id,type,title,description,status,duration,startTime,endTime,epic\n";
    private final static String PATH = "resources/data.csv";

    //метод возвращает объект с историей из файла
    public static FileBackedTasksManager loadedFromFileTasksManager() {
        var fileManager = new FileBackedTasksManager();
        fileManager.loadFromFile();
        return fileManager;
    }

    //метод сохранения данных в файл
    private void save() {
        allTasks.clear();
        allTasks.putAll(getTasks());
        allTasks.putAll(getEpics());
        allTasks.putAll(getSubtasks());
        try (Writer writer = new FileWriter(PATH)) {
            String stringToFile = HEAD + tasksToString() + "\n" + historyToString();
            writer.write(stringToFile);
        } catch (IOException e) {
            throw new ManagerSaveException("Ошибка в работе менеджера");
        }
    }
    //метод загрузки данных из файла, вызывающий методы загрузки задач и истории
    private void loadFromFile() {
        tasksFromString();
        historyFromString();
    }

    private void tasksFromString() {
        String[] lines;
        try {
            lines = Files.readString(Path.of(PATH)).split("\n");
        } catch (IOException e) {
            throw new ManagerSaveException("Не удалось считать файл");
        }
        if (lines.length < 2) return;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String[] lineContents = line.split(",");
            if (lineContents.length >= 5) {
                if (lineContents[1].equals("TASK")) {
                    int id = Integer.parseInt(lineContents[0]);
                    String title = lineContents[2];
                    String description = lineContents[3];
                    Status status = Enum.valueOf(Status.class, lineContents[4]);
                    Duration duration = null;
                    if (!lineContents[5].equals("null")) duration = Duration.parse(lineContents[5]);
                    LocalDateTime startTime = null;
                    if (!lineContents[6].equals("null")) startTime = LocalDateTime.parse(lineContents[6]);
                    Task task = new Task(id, TaskType.TASK, title, description, status, startTime, duration);
                    if (!lineContents[6].equals("null")) task.setEndTime(LocalDateTime.parse(lineContents[7]));
                    tasks.put(id, task);
                    if (getIdCounter() <= id) setIdCounter(++id);
                    prioritizedTasks.add(task);
                }
                if (lineContents[1].equals("EPIC")) {
                    int id = Integer.parseInt(lineContents[0]);
                    String title = lineContents[2];
                    String description = lineContents[3];
                    Status status = Enum.valueOf(Status.class, lineContents[4]);
                    Epic epic = new Epic(id, TaskType.EPIC, title, description, status);
                    epics.put(id, epic);
                    for (Subtask subtask : subtasks.values()) {
                        if (epics.containsKey(subtask.getEpicID())) {
                            epics.get(subtask.getEpicID()).getSubtaskIDs().add(subtask.getId());
                        }
                    }
                    getEpicTimesAndDuration(epics.get(id));
                    if (getIdCounter() <= id) setIdCounter(++id);
                }
                if (lineContents[1].equals("SUBTASK")) {
                    int id = Integer.parseInt(lineContents[0]);
                    String title = lineContents[2];
                    String description = lineContents[3];
                    Status status = Enum.valueOf(Status.class, lineContents[4]);
                    Duration duration = null;
                    if (!lineContents[5].equals("null")) duration = Duration.parse(lineContents[5]);
                    LocalDateTime startTime = null;
                    if (!lineContents[6].equals("null")) startTime = LocalDateTime.parse(lineContents[6]);
                    int epicId = Integer.parseInt(lineContents[8]);
                    Subtask subtask = new Subtask(id, TaskType.SUBTASK, title, description, status, epicId, startTime, duration);
                    if (!lineContents[6].equals("null")) subtask.setEndTime(LocalDateTime.parse(lineContents[7]));
                    subtasks.put(id, subtask);
                    if (epics.containsKey(epicId)) {
                        epics.get(epicId).getSubtaskIDs().add(id);
                    }
                    if (getIdCounter() <= id) setIdCounter(++id);
                    prioritizedTasks.add(subtask);
                    if (epics.containsKey(subtask.getEpicID())) {
                        getEpicTimesAndDuration(epics.get(subtask.getEpicID()));
                    }
                }
            }
        }
    }

    private void historyFromString() {
        String[] lines;
        try {
            lines = Files.readString(Path.of(PATH)).split("\n");
        } catch (IOException e) {
            throw new ManagerSaveException("Не удалось считать файл");
        }
        allTasks.putAll(getTasks());
        allTasks.putAll(getEpics());
        allTasks.putAll(getSubtasks());
        if (lines.length < 4) return;
        if (lines[lines.length - 2].isBlank()) {
            String historyLine = lines[lines.length - 1];
            String[] historyLineContents = historyLine.split(",");
            for (String s : historyLineContents) {
                historyManager.add(allTasks.get(Integer.parseInt(s)));
            }
        }
    }

    private String historyToString() throws IOException {
        if (historyManager.getHistory() == null) return "";
        List<Task> historyList = historyManager.getHistory();
        StringBuilder sb = new StringBuilder();
        for (Task task : historyList) {
            sb.append(task.getId()).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private String tasksToString() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Task task : allTasks.values()) {
            if (task.getType() == TaskType.TASK || task.getType() == TaskType.EPIC) {
                sb.append(task.getId()).append(",").append(task.getType()).append(",").append(task.getTitle())
                        .append(",").append(task.getDescription()).append(",").append(task.getStatus()).append(",")
                        .append(task.getDuration()).append(",")
                        .append(task.getStartTime()).append(",")
                        .append(task.getEndTime()).append("\n");
            } else {
                Subtask subtask = (Subtask) task;
                sb.append(task.getId()).append(",").append(task.getType()).append(",").append(task.getTitle())
                        .append(",").append(task.getDescription()).append(",").append(task.getStatus()).append(",")
                        .append(task.getDuration()).append(",").append(task.getStartTime()).append(",")
                        .append(task.getEndTime()).append(",").append(subtask.getEpicID()).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public int add(Task task) {
        super.add(task);
        save();
        return task.getId();
    }

    @Override
    public int add(Epic epic) {
        super.add(epic);
        save();
        return epic.getId();
    }

    @Override
    public int add(Subtask subtask) {
        super.add(subtask);
        save();
        return subtask.getId();
    }

    @Override
    public Task getTaskById(int id) {
        Task task = super.getTaskById(id);
        save();
        return task;
    }

    @Override
    public Epic getEpicById(int id) {
        Epic epic = super.getEpicById(id);
        save();
        return epic;
    }

    @Override
    public Subtask getSubtaskById(int id) {
        Subtask subtask = super.getSubtaskById(id);
        save();
        return subtask;
    }

    @Override
    public void removeTaskById(int id) {
        prioritizedTasks.remove(tasks.get(id));
        super.removeTaskById(id);
        save();
    }

    @Override
    public void removeEpicById(int id) {
        List<Integer> subtasksInEpic = epics.get(id).getSubtaskIDs();
        for (int subtaskId : subtasksInEpic) {
            prioritizedTasks.remove(subtasks.get(subtaskId));
        }
        prioritizedTasks.remove(epics.get(id));
        super.removeEpicById(id);
        save();
    }

    @Override
    public void removeSubtaskById(Integer id) {
        prioritizedTasks.remove(subtasks.get(id));
        super.removeSubtaskById(id);
        save();
    }
}