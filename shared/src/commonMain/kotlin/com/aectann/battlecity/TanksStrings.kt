package com.aectann.battlecity

import battlecity.shared.generated.resources.Res
import battlecity.shared.generated.resources.ads_age_title
import battlecity.shared.generated.resources.ads_age_prompt
import battlecity.shared.generated.resources.ads_age_label
import battlecity.shared.generated.resources.ads_age_invalid
import battlecity.shared.generated.resources.ads_age_continue
import battlecity.shared.generated.resources.ads_age_skip
import battlecity.shared.generated.resources.resurrection_offer
import battlecity.shared.generated.resources.resurrection_offer_coop
import battlecity.shared.generated.resources.resurrection_watch
import battlecity.shared.generated.resources.resurrection_watching
import battlecity.shared.generated.resources.resurrection_loading
import battlecity.shared.generated.resources.resurrection_unavailable
import battlecity.shared.generated.resources.resurrection_not_earned
import battlecity.shared.generated.resources.resurrection_failed
import battlecity.shared.generated.resources.ads_privacy_options
import battlecity.shared.generated.resources.ads_privacy_failed
import battlecity.shared.generated.resources.endless_over_title
import battlecity.shared.generated.resources.endless_over_wave
import battlecity.shared.generated.resources.leaderboard_portal_note
import battlecity.shared.generated.resources.leaderboard_tab_campaign
import battlecity.shared.generated.resources.leaderboard_tab_endless
import battlecity.shared.generated.resources.leaderboard_wave_row
import battlecity.shared.generated.resources.menu_endless
import battlecity.shared.generated.resources.menu_endless_best
import battlecity.shared.generated.resources.menu_players_one
import battlecity.shared.generated.resources.menu_players_two
import battlecity.shared.generated.resources.tanks_controls_hint_coop
import battlecity.shared.generated.resources.tanks_player_seat
import battlecity.shared.generated.resources.tanks_wave
import battlecity.shared.generated.resources.upgrade_armor
import battlecity.shared.generated.resources.upgrade_armor_desc
import battlecity.shared.generated.resources.upgrade_bulwark
import battlecity.shared.generated.resources.upgrade_bulwark_desc
import battlecity.shared.generated.resources.upgrade_level
import battlecity.shared.generated.resources.upgrade_max
import battlecity.shared.generated.resources.upgrade_piercing
import battlecity.shared.generated.resources.upgrade_piercing_desc
import battlecity.shared.generated.resources.upgrade_rapid_fire
import battlecity.shared.generated.resources.upgrade_rapid_fire_desc
import battlecity.shared.generated.resources.upgrade_ricochet
import battlecity.shared.generated.resources.upgrade_ricochet_desc
import battlecity.shared.generated.resources.upgrade_salvage
import battlecity.shared.generated.resources.upgrade_salvage_desc
import battlecity.shared.generated.resources.upgrade_skip
import battlecity.shared.generated.resources.upgrade_subtitle
import battlecity.shared.generated.resources.upgrade_title
import battlecity.shared.generated.resources.upgrade_treads
import battlecity.shared.generated.resources.upgrade_treads_desc
import battlecity.shared.generated.resources.upgrade_twin_shot
import battlecity.shared.generated.resources.upgrade_twin_shot_desc
import battlecity.shared.generated.resources.app_name
import battlecity.shared.generated.resources.tanks_all_stages_available
import battlecity.shared.generated.resources.tanks_close
import battlecity.shared.generated.resources.tanks_controls_hint
import battlecity.shared.generated.resources.tanks_enemies
import battlecity.shared.generated.resources.tanks_enemy_armor
import battlecity.shared.generated.resources.tanks_enemy_basic
import battlecity.shared.generated.resources.tanks_enemy_fast
import battlecity.shared.generated.resources.tanks_enemy_power
import battlecity.shared.generated.resources.tanks_error_message
import battlecity.shared.generated.resources.tanks_fire
import battlecity.shared.generated.resources.tanks_game_over
import battlecity.shared.generated.resources.tanks_highest_completed_stage
import battlecity.shared.generated.resources.tanks_loading
import battlecity.shared.generated.resources.tanks_locked_stages_hint
import battlecity.shared.generated.resources.tanks_next_stage
import battlecity.shared.generated.resources.tanks_pause
import battlecity.shared.generated.resources.tanks_play_again
import battlecity.shared.generated.resources.tanks_restart
import battlecity.shared.generated.resources.tanks_resume
import battlecity.shared.generated.resources.tanks_retry
import battlecity.shared.generated.resources.tanks_score
import battlecity.shared.generated.resources.tanks_select_stage
import battlecity.shared.generated.resources.tanks_select_stage_title
import battlecity.shared.generated.resources.tanks_sound
import battlecity.shared.generated.resources.tanks_stage
import battlecity.shared.generated.resources.tanks_start
import battlecity.shared.generated.resources.tanks_summary_lives_left
import battlecity.shared.generated.resources.tanks_summary_stage_points
import battlecity.shared.generated.resources.tanks_summary_title
import battlecity.shared.generated.resources.tanks_summary_total_points
import battlecity.shared.generated.resources.tanks_tank_level
import battlecity.shared.generated.resources.about_body
import battlecity.shared.generated.resources.about_title
import battlecity.shared.generated.resources.about_stages
import battlecity.shared.generated.resources.about_version
import battlecity.shared.generated.resources.common_back
import battlecity.shared.generated.resources.common_cancel
import battlecity.shared.generated.resources.common_off
import battlecity.shared.generated.resources.common_on
import battlecity.shared.generated.resources.common_reset
import battlecity.shared.generated.resources.menu_about
import battlecity.shared.generated.resources.menu_continue
import battlecity.shared.generated.resources.menu_new_game
import battlecity.shared.generated.resources.menu_settings
import battlecity.shared.generated.resources.settings_reset_done
import battlecity.shared.generated.resources.settings_reset_progress
import battlecity.shared.generated.resources.settings_reset_question
import battlecity.shared.generated.resources.settings_title
import battlecity.shared.generated.resources.menu_daily
import battlecity.shared.generated.resources.menu_collection
import battlecity.shared.generated.resources.menu_coop_needs_keyboard
import battlecity.shared.generated.resources.pause_exit_confirm
import battlecity.shared.generated.resources.pause_exit_question
import battlecity.shared.generated.resources.pause_exit_title
import battlecity.shared.generated.resources.menu_leaderboard
import battlecity.shared.generated.resources.daily_title
import battlecity.shared.generated.resources.daily_claim
import battlecity.shared.generated.resources.daily_claimed
import battlecity.shared.generated.resources.daily_clock_behind
import battlecity.shared.generated.resources.daily_streak
import battlecity.shared.generated.resources.daily_best_streak
import battlecity.shared.generated.resources.daily_streak_warning
import battlecity.shared.generated.resources.daily_reward_lives
import battlecity.shared.generated.resources.daily_reward_card
import battlecity.shared.generated.resources.daily_banked
import battlecity.shared.generated.resources.collection_title
import battlecity.shared.generated.resources.collection_progress
import battlecity.shared.generated.resources.collection_group_enemies
import battlecity.shared.generated.resources.collection_group_powerups
import battlecity.shared.generated.resources.collection_group_milestones
import battlecity.shared.generated.resources.collection_group_feats
import battlecity.shared.generated.resources.leaderboard_title
import battlecity.shared.generated.resources.leaderboard_empty
import battlecity.shared.generated.resources.leaderboard_local_note
import battlecity.shared.generated.resources.leaderboard_row
import battlecity.shared.generated.resources.nickname_title
import battlecity.shared.generated.resources.nickname_hint
import battlecity.shared.generated.resources.nickname_change
import battlecity.shared.generated.resources.nickname_save
import com.aectann.battlecity.engine.TanksUpgrade
import org.jetbrains.compose.resources.StringResource

/**
 * One place that knows the generated resource accessors, so the screen code stays readable
 * instead of carrying thirty imports of its own.
 */
object TanksStrings {
    val adsAgeTitle = Res.string.ads_age_title
    val adsAgePrompt = Res.string.ads_age_prompt
    val adsAgeLabel = Res.string.ads_age_label
    val adsAgeInvalid = Res.string.ads_age_invalid
    val adsAgeContinue = Res.string.ads_age_continue
    val adsAgeSkip = Res.string.ads_age_skip
    val resurrectionOffer = Res.string.resurrection_offer
    val resurrectionOfferCoop = Res.string.resurrection_offer_coop
    val resurrectionWatch = Res.string.resurrection_watch
    val resurrectionWatching = Res.string.resurrection_watching
    val resurrectionLoading = Res.string.resurrection_loading
    val resurrectionUnavailable = Res.string.resurrection_unavailable
    val resurrectionNotEarned = Res.string.resurrection_not_earned
    val resurrectionFailed = Res.string.resurrection_failed
    val adsPrivacyOptions = Res.string.ads_privacy_options
    val adsPrivacyFailed = Res.string.ads_privacy_failed
    val appName: StringResource = Res.string.app_name
    val loading: StringResource = Res.string.tanks_loading
    val stage: StringResource = Res.string.tanks_stage
    val score: StringResource = Res.string.tanks_score
    val enemies: StringResource = Res.string.tanks_enemies
    val tankLevel: StringResource = Res.string.tanks_tank_level
    val gameOver: StringResource = Res.string.tanks_game_over
    val nextStage: StringResource = Res.string.tanks_next_stage
    val playAgain: StringResource = Res.string.tanks_play_again
    val fire: StringResource = Res.string.tanks_fire
    val start: StringResource = Res.string.tanks_start
    val pause: StringResource = Res.string.tanks_pause
    val resume: StringResource = Res.string.tanks_resume
    val restart: StringResource = Res.string.tanks_restart
    val selectStage: StringResource = Res.string.tanks_select_stage
    val selectStageTitle: StringResource = Res.string.tanks_select_stage_title
    val highestCompletedStage: StringResource = Res.string.tanks_highest_completed_stage
    val lockedStagesHint: StringResource = Res.string.tanks_locked_stages_hint
    val allStagesAvailable: StringResource = Res.string.tanks_all_stages_available
    val errorMessage: StringResource = Res.string.tanks_error_message
    val retry: StringResource = Res.string.tanks_retry
    val summaryTitle: StringResource = Res.string.tanks_summary_title
    val summaryStagePoints: StringResource = Res.string.tanks_summary_stage_points
    val summaryTotalPoints: StringResource = Res.string.tanks_summary_total_points
    val summaryLivesLeft: StringResource = Res.string.tanks_summary_lives_left
    val enemyBasic: StringResource = Res.string.tanks_enemy_basic
    val enemyFast: StringResource = Res.string.tanks_enemy_fast
    val enemyPower: StringResource = Res.string.tanks_enemy_power
    val enemyArmor: StringResource = Res.string.tanks_enemy_armor
    val sound: StringResource = Res.string.tanks_sound
    val close: StringResource = Res.string.tanks_close
    val controlsHint: StringResource = Res.string.tanks_controls_hint

    val menuNewGame: StringResource = Res.string.menu_new_game
    val menuContinue: StringResource = Res.string.menu_continue
    val menuSettings: StringResource = Res.string.menu_settings
    val menuAbout: StringResource = Res.string.menu_about
    val settingsTitle: StringResource = Res.string.settings_title
    val settingsResetProgress: StringResource = Res.string.settings_reset_progress
    val settingsResetQuestion: StringResource = Res.string.settings_reset_question
    val settingsResetDone: StringResource = Res.string.settings_reset_done
    val aboutTitle: StringResource = Res.string.about_title
    val aboutBody: StringResource = Res.string.about_body
    val aboutStages: StringResource = Res.string.about_stages
    val aboutVersion: StringResource = Res.string.about_version
    val commonBack: StringResource = Res.string.common_back
    val commonCancel: StringResource = Res.string.common_cancel
    val commonOn: StringResource = Res.string.common_on
    val commonOff: StringResource = Res.string.common_off
    val commonReset: StringResource = Res.string.common_reset

    // Meta features: dailies, collection, records.
    val menuDaily: StringResource = Res.string.menu_daily
    val menuCollection: StringResource = Res.string.menu_collection
    val menuLeaderboard: StringResource = Res.string.menu_leaderboard
    val dailyTitle: StringResource = Res.string.daily_title
    val dailyClaim: StringResource = Res.string.daily_claim
    val dailyClaimed: StringResource = Res.string.daily_claimed
    val dailyClockBehind: StringResource = Res.string.daily_clock_behind
    val dailyStreak: StringResource = Res.string.daily_streak
    val dailyBestStreak: StringResource = Res.string.daily_best_streak
    val dailyStreakWarning: StringResource = Res.string.daily_streak_warning
    val dailyRewardLives: StringResource = Res.string.daily_reward_lives
    val dailyRewardCard: StringResource = Res.string.daily_reward_card
    val dailyBanked: StringResource = Res.string.daily_banked
    val collectionTitle: StringResource = Res.string.collection_title
    val collectionProgress: StringResource = Res.string.collection_progress
    val collectionGroupEnemies: StringResource = Res.string.collection_group_enemies
    val collectionGroupPowerups: StringResource = Res.string.collection_group_powerups
    val collectionGroupMilestones: StringResource = Res.string.collection_group_milestones
    val collectionGroupFeats: StringResource = Res.string.collection_group_feats
    val leaderboardTitle: StringResource = Res.string.leaderboard_title
    val leaderboardEmpty: StringResource = Res.string.leaderboard_empty
    val leaderboardLocalNote: StringResource = Res.string.leaderboard_local_note
    val leaderboardRow: StringResource = Res.string.leaderboard_row
    val nicknameTitle: StringResource = Res.string.nickname_title
    val nicknameHint: StringResource = Res.string.nickname_hint
    val nicknameChange: StringResource = Res.string.nickname_change
    val nicknameSave: StringResource = Res.string.nickname_save

    // Co-op and endless.
    val menuPlayersOne: StringResource = Res.string.menu_players_one
    val menuPlayersTwo: StringResource = Res.string.menu_players_two
    val menuCoopNeedsKeyboard: StringResource = Res.string.menu_coop_needs_keyboard
    val pauseExitTitle: StringResource = Res.string.pause_exit_title
    val pauseExitQuestion: StringResource = Res.string.pause_exit_question
    val pauseExitConfirm: StringResource = Res.string.pause_exit_confirm
    val menuEndless: StringResource = Res.string.menu_endless
    val menuEndlessBest: StringResource = Res.string.menu_endless_best
    val wave: StringResource = Res.string.tanks_wave
    val playerSeat: StringResource = Res.string.tanks_player_seat
    val controlsHintCoop: StringResource = Res.string.tanks_controls_hint_coop
    val upgradeTitle: StringResource = Res.string.upgrade_title
    val upgradeSubtitle: StringResource = Res.string.upgrade_subtitle
    val upgradeSkip: StringResource = Res.string.upgrade_skip
    val upgradeLevel: StringResource = Res.string.upgrade_level
    val upgradeMax: StringResource = Res.string.upgrade_max
    val endlessOverTitle: StringResource = Res.string.endless_over_title
    val endlessOverWave: StringResource = Res.string.endless_over_wave
    val leaderboardTabCampaign: StringResource = Res.string.leaderboard_tab_campaign
    val leaderboardTabEndless: StringResource = Res.string.leaderboard_tab_endless
    val leaderboardWaveRow: StringResource = Res.string.leaderboard_wave_row
    val leaderboardPortalNote: StringResource = Res.string.leaderboard_portal_note

    fun enemyName(type: String): StringResource = when (type) {
        "fast" -> enemyFast
        "power" -> enemyPower
        "armor" -> enemyArmor
        else -> enemyBasic
    }

    /**
     * The upgrade card's two lines. Exhaustive over [TanksUpgrade] on purpose: adding an
     * upgrade without a name should fail to compile rather than ship as a blank card.
     */
    fun upgradeName(upgrade: TanksUpgrade): StringResource = when (upgrade) {
        TanksUpgrade.RapidFire -> Res.string.upgrade_rapid_fire
        TanksUpgrade.TwinShot -> Res.string.upgrade_twin_shot
        TanksUpgrade.Armor -> Res.string.upgrade_armor
        TanksUpgrade.Ricochet -> Res.string.upgrade_ricochet
        TanksUpgrade.Treads -> Res.string.upgrade_treads
        TanksUpgrade.Piercing -> Res.string.upgrade_piercing
        TanksUpgrade.Bulwark -> Res.string.upgrade_bulwark
        TanksUpgrade.Salvage -> Res.string.upgrade_salvage
    }

    fun upgradeDescription(upgrade: TanksUpgrade): StringResource = when (upgrade) {
        TanksUpgrade.RapidFire -> Res.string.upgrade_rapid_fire_desc
        TanksUpgrade.TwinShot -> Res.string.upgrade_twin_shot_desc
        TanksUpgrade.Armor -> Res.string.upgrade_armor_desc
        TanksUpgrade.Ricochet -> Res.string.upgrade_ricochet_desc
        TanksUpgrade.Treads -> Res.string.upgrade_treads_desc
        TanksUpgrade.Piercing -> Res.string.upgrade_piercing_desc
        TanksUpgrade.Bulwark -> Res.string.upgrade_bulwark_desc
        TanksUpgrade.Salvage -> Res.string.upgrade_salvage_desc
    }
}
