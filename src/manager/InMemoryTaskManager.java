package manager;

import filemanager.ManagerSaveException;
import tasks.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class InMemoryTaskManager implements TaskManager {
    protected static int idCounter = 1;
    public final HistoryManager historyManager = Managers.getDefaultHistory();
    protected Map<Integer, Task> tasks = new HashMap<>();
    protected Map<Integer, Epic> epics = new HashMap<>();
    protected Map<Integer, Subtask> subtasks = new HashMap<>();

    public Map<Integer, Task> getTasks() {
        return tasks;
    }

    public Map<Integer, Epic> getEpics() {
        return epics;
    }

    public Map<Integer, Subtask> getSubtasks() {
        return subtasks;
    }

    protected Set<Task> prioritizedTasks = new TreeSet<>((o1, o2) -> {
        if (o1.getStartTime() == null) return 1;
        if (o2.getStartTime() == null) return -1;
        if (o1.getStartTime().isAfter(o2.getStartTime())) return 1;
        if (o1.getStartTime().isBefore(o2.getStartTime())) return -1;
        return 0;
    });

    public static int getIdCounter() {
        return idCounter;
    }

    public static void setIdCounter(int idCounter) {
        InMemoryTaskManager.idCounter = idCounter;
    }

    @Override
    public void intersectionCheck() {
        LocalDateTime checkTime = null;
        boolean flagCheckTimeIsEmpty = true;
        for (Task task : prioritizedTasks) {
            if (flagCheckTimeIsEmpty) {
                checkTime = task.getEndTime();
                flagCheckTimeIsEmpty = false;
            } else {
                if (task.getStartTime().isBefore(checkTime)) {
                    throw new ManagerSaveException("Найдено пересечение времени задач, проверьте корректность данных");
                }
                if (task.getStartTime().isAfter(checkTime) || task.getStartTime().isEqual(checkTime)) {
                    checkTime = task.getEndTime();
                }
            }
        }
    }

    @Override
    public void getPrioritizedTasks() {
        intersectionCheck();
        for (Task task : prioritizedTasks) {
            System.out.println(task);
        }
    }

    @Override
    public void getTaskEndTime(Task task) {
        LocalDateTime endTime = task.getStartTime().plus(task.getDuration());
        task.setEndTime(endTime);
    }

    @Override
    public void getEpicTimesAndDuration(Epic epic) {
        if (epic.getSubtaskIDs().isEmpty()) {
            return;
        }
        LocalDateTime start;
        LocalDateTime end;
        start = subtasks.get(epic.getSubtaskIDs().get(0)).getStartTime();
        end = subtasks.get(epic.getSubtaskIDs().get(0)).getEndTime();
        epic.setStartTime(start);
        epic.setEndTime(end);
        for (Integer id : epic.getSubtaskIDs()) {
            if (subtasks.get(id).getStartTime().isBefore(start)) {
                start = subtasks.get(id).getStartTime();
            }
            if (subtasks.get(id).getEndTime().isAfter(end)) {
                end = subtasks.get(id).getEndTime();
            }
        }
        epic.setStartTime(start);
        epic.setEndTime(end);
        epic.setDuration(Duration.between(epic.getStartTime(), epic.getEndTime()));
    }

    @Override
    public void getSubtaskEndTime(Subtask subtask) {
        LocalDateTime endTime = subtask.getStartTime().plus(subtask.getDuration());
        subtask.setEndTime(endTime);
        if (epics.containsKey(subtask.getEpicID())) {
            getEpicTimesAndDuration(epics.get(subtask.getEpicID()));
        }
    }

    @Override
    public Task getTaskById(int id) {
        historyManager.add(tasks.get(id));
        return tasks.get(id);
    }

    public Epic getEpicById(int id) {
        historyManager.add(epics.get(id));
        return epics.get(id);
    }

    public Subtask getSubtaskById(int id) {
        historyManager.add(subtasks.get(id));
        return subtasks.get(id);
    }

    @Override
    public int add(Task task) {
        task.setId(idCounter++);
        tasks.put(task.getId(), task);
        return task.getId();
    }

    @Override
    public int add(Epic epic) {
        epic.setId(idCounter++);
        epics.put(epic.getId(), epic);
        for (Subtask subtask : subtasks.values()) {
            if(epic.getId() == subtask.getEpicID()) {
                epic.getSubtaskIDs().add(subtask.getId());
            }
        }
        return epic.getId();
    }

    @Override
    public int add(Subtask subtask) {
        subtask.setId(idCounter++);
        subtasks.put(subtask.getId(), subtask);
        for (Epic epic : epics.values()) {
            if(epic.getId() == subtask.getEpicID()) {
                epic.getSubtaskIDs().add(subtask.getId());
            }
        }
        if (epics.containsKey(subtask.getEpicID())) {
            update(epics.get(subtask.getEpicID()));
        }
        return subtask.getId();
    }
    @Override
    public int update(Task task) {
        tasks.put(task.getId(), task);
        return task.getId();
    }

    @Override
    public int update(Epic epic) {
        epic.setStatus(Status.NEW);
        epics.put(epic.getId(), epic);
        List<Integer> epicList = epic.getSubtaskIDs();
        if (epic.getSubtaskIDs().isEmpty()) {
            return epic.getId();
        }
        for (int id : epicList) {
            if (subtasks.get(id).getStatus() == Status.DONE) {
                epic.setStatus(Status.DONE);
                break;
            } else if (subtasks.get(id).getStatus() == Status.IN_PROGRESS) {
                epic.setStatus(Status.IN_PROGRESS);
                return epic.getId();
            } else if (epic.getStatus() == Status.DONE && subtasks.get(id).getStatus() == Status.NEW) {
                epic.setStatus(Status.IN_PROGRESS);
                return epic.getId();
            }
        }
        return epic.getId();
    }
    @Override
    public int update(Subtask subtask) {
        subtasks.put(subtask.getId(), subtask);
        update(epics.get(subtask.getEpicID()));
        return subtask.getId();
    }

    @Override
    public void removeAllTasks() {
        tasks.clear();
    }

    @Override
    public void removeAllEpicsAndSubtasks() {
        epics.clear();
        subtasks.clear();
    }

    @Override
    public void removeTaskById(int id) {
        historyManager.removeFromHistory(tasks.get(id));
        tasks.remove(id);
    }

    @Override
    public void removeEpicById(int id) {
        List<Integer> subtasksInEpic = epics.get(id).getSubtaskIDs();
        for (int subtaskId : subtasksInEpic) {
            historyManager.removeFromHistory(subtasks.get(subtaskId));
            subtasks.remove(subtaskId);
        }
        historyManager.removeFromHistory(epics.get(id));
        epics.remove(id);
    }

    @Override
    public void removeSubtaskById(Integer id) {
        int epicID = subtasks.get(id).getEpicID();
        epics.get(epicID).getSubtaskIDs().remove(id);
        update(epics.get(epicID));
        historyManager.removeFromHistory(subtasks.get(id));
        subtasks.remove(id);
    }

    @Override
    public List<Subtask> getAllSubtasksByEpic(int id) {
        List<Integer> numbers = epics.get(id).getSubtaskIDs();
        List<Subtask> subtaskArrayList = new ArrayList<>();
        for (int item : numbers) {
            subtaskArrayList.add(subtasks.get(item));
        }
        return subtaskArrayList;
    }

    @Override
    public List<Task> getHistory() {
        return historyManager.getHistory();
    }

    @Override
    public String toString() {
        return "TaskManager{" +
                "tasks=" + tasks +
                ", epics=" + epics +
                ", subtasks=" + subtasks +
                '}';
    }
}