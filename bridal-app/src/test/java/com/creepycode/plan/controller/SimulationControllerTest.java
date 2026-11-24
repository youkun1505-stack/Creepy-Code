package com.creepycode.plan.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.PriceRevisionItemDto;
import com.creepycode.plan.dto.PriceRevisionSummaryDto;
import com.creepycode.plan.dto.SimulationResultDto;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PriceMasterRepository;
import com.creepycode.plan.service.PriceRevisionService;
import com.creepycode.plan.service.SimulationService;

/**
 * 料金シミュレーション画面（P-13）の Controller のテスト。
 * 画面の表示、認可、価格改定情報の表示、および最新価格更新処理を検証する。
 */
@WebMvcTest(SimulationController.class)
@Import(SecurityConfig.class)
class SimulationControllerTest {

    private static final String PATH = "/plan/simulation";

    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", "pass", "テスト 顧客", UserRole.CUSTOMER);

    private static final LoginUser PLANNER =
            new LoginUser(2L, "planner@example.com", "pass", "テスト プランナー", UserRole.PLANNER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    @MockitoBean
    private PriceMasterRepository priceMasterRepository;

    @MockitoBean
    private OnboardingAnswerRepository onboardingAnswerRepository;

    @MockitoBean
    private PriceRevisionService priceRevisionService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("未ログイン時はログイン画面へリダイレクトされること")
    void show_whenNotAuthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    @DisplayName("顧客ログイン時：事前ヒアリングに予算設定がある場合、その予算で画面が表示されること")
    void show_whenCustomerLoggedInWithBudget_rendersSimulationViewWithCustomerBudget() throws Exception {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(CUSTOMER.getUserId());
        answer.setBudget(2500000);
        when(onboardingAnswerRepository.findFirstByUserIdOrderByCreatedAtDesc(CUSTOMER.getUserId()))
                .thenReturn(Optional.of(answer));

        PriceMaster item1 = new PriceMaster();
        item1.setItemId(1L);
        item1.setItemName("チャペル挙式");
        item1.setCategory("会場");
        item1.setBasePrice(200000);
        item1.setIsVariable("N");

        PriceMaster item2 = new PriceMaster();
        item2.setItemId(2L);
        item2.setItemName("フレンチコース");
        item2.setCategory("料理");
        item2.setBasePrice(15000);
        item2.setIsVariable("Y");

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc())
                .thenReturn(List.of(item1, item2));

        SimulationResultDto initialResult = new SimulationResultDto(Collections.emptyList(), 0L, 2500000);
        when(simulationService.calculate(anyInt(), any())).thenReturn(initialResult);

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("plan/simulation"))
                .andExpect(model().attribute("budget", 2500000))
                .andExpect(model().attribute("categorizedItems", hasKey("会場")))
                .andExpect(model().attribute("categorizedItems", hasKey("料理")))
                .andExpect(model().attribute("initialResult", initialResult));
    }

    @Test
    @DisplayName("顧客ログイン時かつ価格改定がある場合、モデルに改定サマリーが含まれること")
    void show_whenCustomerHasPriceRevisions_rendersRevisionSummary() throws Exception {
        when(onboardingAnswerRepository.findFirstByUserIdOrderByCreatedAtDesc(CUSTOMER.getUserId()))
                .thenReturn(Optional.empty());
        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc())
                .thenReturn(Collections.emptyList());

        SimulationResultDto initialResult = new SimulationResultDto(Collections.emptyList(), 0L, 3000000);
        when(simulationService.calculate(anyInt(), any())).thenReturn(initialResult);

        PriceRevisionItemDto revItem = new PriceRevisionItemDto(
                101L, 1L, "チャペル挙式", "会場", 1, 180000, 200000, false
        );
        PriceRevisionSummaryDto summary = new PriceRevisionSummaryDto(10L, false, List.of(revItem));
        when(priceRevisionService.checkRevisionsForUser(CUSTOMER.getUserId())).thenReturn(Optional.of(summary));

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("plan/simulation"))
                .andExpect(model().attribute("revisionSummary", summary));
    }

    @Test
    @DisplayName("最新価格に更新（POST）：更新サービスが呼ばれ、シミュレーション画面へリダイレクトされること")
    void updateToLatestPrices_whenValid_redirectsToSimulationWithSuccessMessage() throws Exception {
        PriceRevisionSummaryDto updated = new PriceRevisionSummaryDto(10L, false, Collections.emptyList());
        when(priceRevisionService.updateToLatestPrices(eq(10L), eq(CUSTOMER.getUserId()))).thenReturn(updated);

        mockMvc.perform(post("/plan/simulation/price-revision/update")
                .with(user(CUSTOMER))
                .with(csrf())
                .param("planId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plan/simulation"))
                .andExpect(flash().attribute("successMessage", "プランのオプションを最新の価格に更新しました。"));

        verify(priceRevisionService).updateToLatestPrices(10L, CUSTOMER.getUserId());
    }

    @Test
    @DisplayName("顧客ログイン時：事前ヒアリング未回答の場合、デフォルト予算300万円で画面が表示されること")
    void show_whenCustomerLoggedInWithoutAnswer_rendersSimulationViewWithDefaultBudget() throws Exception {
        when(onboardingAnswerRepository.findFirstByUserIdOrderByCreatedAtDesc(CUSTOMER.getUserId()))
                .thenReturn(Optional.empty());

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc())
                .thenReturn(Collections.emptyList());

        SimulationResultDto initialResult = new SimulationResultDto(Collections.emptyList(), 0L, 3000000);
        when(simulationService.calculate(anyInt(), any())).thenReturn(initialResult);

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("plan/simulation"))
                .andExpect(model().attribute("budget", 3000000));
    }

    @Test
    @DisplayName("プランナーログイン時も画面が表示できること")
    void show_whenPlannerLoggedIn_rendersSimulationView() throws Exception {
        when(onboardingAnswerRepository.findFirstByUserIdOrderByCreatedAtDesc(PLANNER.getUserId()))
                .thenReturn(Optional.empty());
        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc())
                .thenReturn(Collections.emptyList());

        SimulationResultDto initialResult = new SimulationResultDto(Collections.emptyList(), 0L, 3000000);
        when(simulationService.calculate(anyInt(), any())).thenReturn(initialResult);

        mockMvc.perform(get(PATH).with(user(PLANNER)))
                .andExpect(status().isOk())
                .andExpect(view().name("plan/simulation"))
                .andExpect(model().attribute("budget", 3000000));
    }
}
