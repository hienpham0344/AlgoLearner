package com.algolearner.platform.course;

import com.algolearner.platform.course.CourseDtos.Block;
import com.algolearner.platform.course.CourseDtos.CourseContent;
import com.algolearner.platform.course.CourseDtos.LessonContent;
import com.algolearner.platform.course.CourseDtos.OptionContent;
import com.algolearner.platform.course.CourseDtos.QuestionContent;
import com.algolearner.platform.course.CourseDtos.QuizContent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure validation and id normalization of course content (FR-PCRS-008, 010, 011). */
public final class CourseContentRules {
    private CourseContentRules() { }

    /** Assigns missing ids, validates structure and references. {@code publishedProblem} checks practice links. */
    public static CourseContent normalize(CourseContent content, boolean forPublication, Predicate<UUID> publishedProblem) {
        if (content == null) throw CourseException.invalid("Course content is required");
        String title = requireText(content.title(), 255, "Course title is required");
        List<QuizContent> quizzes = new ArrayList<>();
        Set<UUID> quizIds = new HashSet<>();
        Set<UUID> lessonIds = new HashSet<>();
        List<LessonContent> lessons = new ArrayList<>();
        for (LessonContent lesson : nullSafe(content.lessons())) {
            UUID id = lesson.id() == null ? UUID.randomUUID() : lesson.id();
            if (!lessonIds.add(id)) throw CourseException.invalid("Duplicate lesson id");
            lessons.add(new LessonContent(id, requireText(lesson.title(), 255, "Lesson title is required"),
                    lesson.estimatedMinutes(), blankToNull(lesson.prerequisiteNote()), lesson.preview(), lesson.blocks()));
        }
        for (QuizContent quiz : nullSafe(content.quizzes())) {
            UUID id = quiz.id() == null ? UUID.randomUUID() : quiz.id();
            if (!quizIds.add(id)) throw CourseException.invalid("Duplicate quiz id");
            if (quiz.lessonId() != null && !lessonIds.contains(quiz.lessonId())) throw CourseException.invalid("Quiz references an unknown lesson");
            quizzes.add(normalizeQuiz(quiz, id));
        }
        for (LessonContent lesson : lessons) {
            if (lesson.blocks() == null || lesson.blocks().isEmpty()) throw CourseException.invalid("Lesson must contain content");
            for (Block block : lesson.blocks()) validateBlock(block, quizIds, publishedProblem);
        }
        if (forPublication && lessons.isEmpty()) throw CourseException.unprocessable("COURSE_EMPTY", "Course must contain a lesson");
        return new CourseContent(title, blankToNull(content.description()), content.level(), blankToNull(content.topic()),
                content.estimatedMinutes(), List.copyOf(lessons), List.copyOf(quizzes));
    }

    static QuizContent normalizeQuiz(QuizContent quiz, UUID id) {
        String title = requireText(quiz.title(), 255, "Quiz title is required");
        if (quiz.completionRule() == CourseDtos.CompletionRule.PASSING_SCORE && quiz.passingScore() == null) {
            throw CourseException.invalid("Passing score is required for PASSING_SCORE quizzes");
        }
        if (quiz.questions() == null || quiz.questions().isEmpty()) throw CourseException.invalid("Quiz must contain a question");
        Set<UUID> questionIds = new HashSet<>();
        List<QuestionContent> questions = new ArrayList<>();
        for (QuestionContent question : quiz.questions()) {
            UUID questionId = question.id() == null ? UUID.randomUUID() : question.id();
            if (!questionIds.add(questionId)) throw CourseException.invalid("Duplicate question id");
            Set<UUID> optionIds = new HashSet<>();
            List<OptionContent> options = new ArrayList<>();
            for (OptionContent option : nullSafe(question.options())) {
                UUID optionId = option.id() == null ? UUID.randomUUID() : option.id();
                if (!optionIds.add(optionId)) throw CourseException.invalid("Duplicate option id");
                options.add(new OptionContent(optionId, requireText(option.text(), 1000, "Option text is required"), option.correct()));
            }
            long correct = options.stream().filter(OptionContent::correct).count();
            boolean valid = switch (question.type()) {
                case SINGLE_CHOICE -> options.size() >= 2 && correct == 1;
                case TRUE_FALSE -> options.size() == 2 && correct == 1;
                case MULTIPLE_CHOICE -> options.size() >= 2 && correct >= 1;
            };
            if (!valid) throw CourseException.invalid("Question options do not match the question type");
            if (question.points() == null || question.points().signum() <= 0) throw CourseException.invalid("Question points must be positive");
            questions.add(new QuestionContent(questionId, requireText(question.prompt(), 5000, "Question prompt is required"),
                    question.type(), question.points(), blankToNull(question.explanation()), List.copyOf(options)));
        }
        return new QuizContent(id, quiz.lessonId(), title, quiz.mandatory(), quiz.completionRule(),
                quiz.completionRule() == CourseDtos.CompletionRule.PASSING_SCORE ? quiz.passingScore() : null,
                quiz.maxAttempts(), quiz.timeLimitSeconds(), quiz.answerVisibility(), List.copyOf(questions));
    }

    static void validateBlock(Block block, Set<UUID> quizIds, Predicate<UUID> publishedProblem) {
        if (block == null || block.type() == null) throw CourseException.invalid("Content block type is required");
        switch (block.type()) {
            case TEXT -> requireText(block.text(), 100_000, "Text block is empty");
            case CODE -> requireText(block.code(), 50_000, "Code example is empty");
            case IMAGE -> requireImage(block.imageUrl());
            case QUIZ -> { if (block.quizId() == null || !quizIds.contains(block.quizId())) throw CourseException.invalid("Quiz block references an unknown quiz"); }
            case PRACTICE_LINK -> {
                if (block.problemId() == null || !publishedProblem.test(block.problemId())) {
                    throw CourseException.invalid("Practice link must reference a published public problem");
                }
            }
        }
    }

    private static void requireImage(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) throw CourseException.invalid("Image URL is required");
        try {
            URI uri = new URI(url.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw CourseException.invalid("Image must be an https URL");
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
            if (!path.matches(".*\\.(png|jpe?g|gif|webp|svg)$")) throw CourseException.invalid("Image URL must point to a png, jpg, gif, webp or svg file");
        } catch (java.net.URISyntaxException error) {
            throw CourseException.invalid("Image URL is malformed");
        }
    }

    static String requireText(String text, int max, String message) {
        if (text == null || text.isBlank() || text.length() > max) throw CourseException.invalid(message);
        return text.strip();
    }

    static String blankToNull(String text) { return text == null || text.isBlank() ? null : text.strip(); }

    private static <T> List<T> nullSafe(List<T> values) { return values == null ? List.of() : values; }
}
