package com.creepycode.plan.service;

import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.repository.PlanRepository;

/**
 * プランの大枠を AI に作らせて、PLANS に保存する（作り直しも同じ）。あわせて会話履歴の md を書く。
 * AI を待つあいだ DB 接続を握らないよう、このクラスのメソッドはトランザクションで包まない
 * （接続は1人2本までのため。application.properties の hikari.maximum-pool-size）。
 */
@Service
public class PlanGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanGenerationService.class);

    private final OnboardingAnswerRepository answerRepository;
    private final PlanRepository planRepository;
    private final PlanMaterialService materialService;
    private final PlanDraftAiService draftAiService;
    private final PlanDraftJson draftJson;
    private final ConversationLogService conversationLogService;

    // いまプランを作っている顧客（USER_ID）。アプリ1つの中で、同じ顧客の生成が重ならないようにする
    private final Map<Long, Boolean> running = new ConcurrentHashMap<>();

    public PlanGenerationService(OnboardingAnswerRepository answerRepository, PlanRepository planRepository,
            PlanMaterialService materialService, PlanDraftAiService draftAiService, PlanDraftJson draftJson,
            ConversationLogService conversationLogService) {
        this.answerRepository = answerRepository;
        this.planRepository = planRepository;
        this.materialService = materialService;
        this.draftAiService = draftAiService;
        this.draftJson = draftJson;
        this.conversationLogService = conversationLogService;
    }

    /** プランを作れる（STEP4 まで答えている＝いちばん新しい回答の UNDECIDED_TAGS が NULL でない）かを返す。 */
    public boolean isReady(Long userId) {
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(answer -> answer.getUndecidedTags() != null)
                .orElse(false);
    }

    /**
     * 顧客のいちばん新しい回答から、プランの大枠と価値観の要点を作って保存する。
     * - STEP4 まで答えていない（UNDECIDED_TAGS が NULL）ときは作らない（NOT_READY）
     * - プランナーに送った後は作らない（SENT_TO_PLANNER）
     * - 今の回答のプランがもうできているときは作らない（ALREADY_GENERATED）
     * - はじめてのときは、先に STATUS＝CREATING の行を作ってから AI を呼ぶ
     * - 作り直し（プランの回答より新しい回答がある）のときは、成功するまで今のプランを変えない
     * - 失敗したとき（AI の失敗・返事の形の誤り）は、プランの中身を変えずに FAILED を返す
     */
    public PlanGenerationResult generate(Long userId) {
        // 同じ顧客の生成が重なると、同じ回答の行を2つ作ろうとしたり、md に2回書いたりするので、1つずつにする。
        // P-11 は30秒で［やり直す］を出すが、AI もそれまで待つため、前の呼び出しがまだ動いていることがある
        if (running.putIfAbsent(userId, Boolean.TRUE) != null) {
            return PlanGenerationResult.IN_PROGRESS;
        }
        try {
            return generateOnce(userId);
        } catch (DataIntegrityViolationException e) {
            // 別の PC で同じ顧客が同時に作ったときなど、DB の重複の決まりに当たったとき
            LOGGER.warn("プランを保存できませんでした（userId={}）：{}", userId, e.getMessage());
            return PlanGenerationResult.FAILED;
        } finally {
            running.remove(userId);
        }
    }

    private PlanGenerationResult generateOnce(Long userId) {
        Optional<OnboardingAnswer> latest = answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (latest.isEmpty() || latest.get().getUndecidedTags() == null) {
            return PlanGenerationResult.NOT_READY;
        }
        OnboardingAnswer answer = latest.get();

        Optional<Plan> existing = planRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (existing.isPresent()) {
            Plan plan = existing.get();
            if (plan.getStatus().isSentToPlanner()) {
                return PlanGenerationResult.SENT_TO_PLANNER;
            }
            if (plan.getStatus() == PlanStatus.CUSTOMER_DRAFT_REVIEW
                    && Objects.equals(plan.getAnswerId(), answer.getAnswerId())) {
                return PlanGenerationResult.ALREADY_GENERATED;
            }
        }
        Plan plan = existing.orElseGet(() -> planRepository.save(newPlan(userId, answer)));

        PlanMaterial material = materialService.build(userId, answer);
        PlanDraftJson.ParsedPlan parsed;
        try {
            parsed = draftJson.parseAiResponse(draftAiService.requestDraft(material));
        } catch (AiException | PlanDraftFormatException e) {
            LOGGER.warn("プランを作れませんでした（userId={}, planId={}）：{}", userId, plan.getPlanId(),
                    e.getMessage());
            return PlanGenerationResult.FAILED;
        }

        // AI を待つあいだにプランナーへ送られていたら、上書きしない（DB から読み直して確かめる）
        Optional<Plan> current = planRepository.findById(plan.getPlanId());
        if (current.isPresent() && current.get().getStatus().isSentToPlanner()) {
            return PlanGenerationResult.SENT_TO_PLANNER;
        }
        if (current.isPresent()) {
            plan = current.get();
        }

        // 成功したときだけ、回答の付け替えと中身の書き換えをまとめて行う
        plan.setAnswerId(answer.getAnswerId());
        plan.setAiDraft(draftJson.toDraftJson(parsed.draft()));
        plan.setValueSummary(draftJson.toValuesJson(parsed.values()));
        plan.setStatus(PlanStatus.CUSTOMER_DRAFT_REVIEW);
        plan.setUpdatedAt(LocalDateTime.now());
        Plan saved = planRepository.save(plan);

        try {
            conversationLogService.record(userId, saved.getPlanId(), material, parsed.draft(), parsed.values());
        } catch (UncheckedIOException e) {
            // md は記録用なので、書けなくてもプランはできたものとして進める。
            // 書けなかった回の記録は戻らない（ログに残すだけ）。次に作り直したときは、その回の分が書かれる
            LOGGER.error("会話履歴の md を書けませんでした（userId={}, planId={}）", userId, saved.getPlanId(), e);
        }
        return PlanGenerationResult.GENERATED;
    }

    private Plan newPlan(Long userId, OnboardingAnswer answer) {
        LocalDateTime now = LocalDateTime.now();
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setAnswerId(answer.getAnswerId());
        plan.setStatus(PlanStatus.CREATING);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        return plan;
    }
}
