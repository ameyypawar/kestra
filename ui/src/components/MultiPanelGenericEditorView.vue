<template>
    <div class="main-editor">
        <MultiPanelEditorTabs :tabs="editorElements" @update:tabs="setTabValue" :openTabs="openTabs">
            <div class="tabs-actions">
                <KsButton
                    :icon="splitOrientation === 'vertical' ? ViewSplitVertical : ViewSplitHorizontal"
                    :tooltip="splitOrientation === 'vertical' ? t('split_horizontal') : t('split_vertical')"
                    class="orientation-toggle"
                    @click="toggleOrientation"
                />
                <slot name="actions" />
            </div>
        </MultiPanelEditorTabs>
        <div class="editor-wrapper">
            <!-- Fixed vertical: this splitter stacks the editor above the bottom
                 panel (the playground), which is not what the toggle is about. -->
            <KsSplitter class="default-theme editor-panels" layout="vertical">
                <KsSplitterPanel>
                    <MultiPanelTabs v-model="panels" :layout="splitOrientation" @remove-tab="onRemoveTab" />
                </KsSplitterPanel>
                <KsSplitterPanel v-if="bottomVisible && slots['bottom-panel']">
                    <slot name="bottom-panel" />
                </KsSplitterPanel>
            </KsSplitter>
        </div>
        <slot name="footer" />
    </div>
</template>

<script lang="ts" setup>
    import {computed, useSlots} from "vue"
    import {useStorage} from "@vueuse/core"
    import {useI18n} from "vue-i18n"
    import ViewSplitVertical from "vue-material-design-icons/ViewSplitVertical.vue"
    import ViewSplitHorizontal from "vue-material-design-icons/ViewSplitHorizontal.vue"
    import MultiPanelEditorTabs from "./MultiPanelEditorTabs.vue"
    import MultiPanelTabs from "./MultiPanelTabs.vue"
    import {EditorElement, Panel} from "../utils/multiPanelTypes"
    import {useStoredPanels} from "../composables/useStoredPanels"

    const {t} = useI18n()

    /**
     * How the editor panels sit next to each other.
     *
     * `horizontal` (the default) keeps them side by side, which is how the editor
     * has always opened. This drives the splitter inside MultiPanelTabs — it used
     * to drive the outer one, which holds a second panel only in playground mode
     * and so ignored the setting entirely the rest of the time.
     *
     * The key is deliberately not the old `editor-split-orientation`: that value
     * described the outer splitter, where the same two words meant the opposite
     * arrangement, and reusing it would flip existing users' panels to stacked.
     */
    const splitOrientation = useStorage<"vertical" | "horizontal">("editor-panels-orientation", "horizontal")

    function toggleOrientation() {
        splitOrientation.value = splitOrientation.value === "vertical" ? "horizontal" : "vertical"
    }

    const props = withDefaults(defineProps<{
        editorElements: EditorElement[];
        defaultActiveTabs: string[];
        saveKey?: string;
        bottomVisible?: boolean;
        preSerializePanels?: (panels: Panel[]) => any;
    }>(), {
        bottomVisible: false,
        preSerializePanels: undefined,
        saveKey: undefined,
    })

    const slots = useSlots()

    const defaultPanelSize = computed(() => panels.value.length ? panels.value.reduce((acc, panel) => acc + panel.size, 0) / panels.value.length : 1)

    function focusTab(tabValue: string){
        for(const panel of panels.value){
            const t = panel.tabs.find(e => e.uid === tabValue)
            if(t) panel.activeTab = t
        }
    }

    function getPanelFromValue(value: string): {panel: Panel, prepend: boolean} | undefined {
        for (const element of props.editorElements) {
            const deserializedTab = element.deserialize(value, true)
            if (deserializedTab) {
                return {
                    panel: {
                        activeTab: deserializedTab,
                        tabs: [deserializedTab],
                        size: defaultPanelSize.value,
                    },
                    prepend: element.prepend ?? false,
                }
            }
        }
    };

    const {panels, saveState} = useStoredPanels(
        props.saveKey,
        props.editorElements,
        props.defaultActiveTabs,
        props.preSerializePanels,
    )

    const emit = defineEmits<{
        (e: "set-tab-value", tabValue: string): void | false;
        (e: "remove-tab", tabValue: string): void;
    }>()

    function setTabValue(tabValue: string){
        if(props.editorElements.find(e => e.uid === tabValue)?.button.disabled){
            return
        }

        if(emit("set-tab-value", tabValue) === false) {
            return
        }

        if(openTabs.value.includes(tabValue)){
            onRemoveTab(tabValue)
            return
        }

        const panel = getPanelFromValue(tabValue)
        if(panel){
            if(panel.prepend){
                panels.value.unshift(panel.panel)
            } else {
                panels.value.push(panel.panel)
            }
        }
    }

    const openTabs = computed(() => panels.value.flatMap(p => p.tabs.map(t => t.uid)))

    function onRemoveTab(tabValue: string) {
        const panel = panels.value.find(p => p.tabs.some(t => t.uid === tabValue))
        if (panel) {
            panel.tabs = panel.tabs.filter(t => t.uid !== tabValue)
            if (panel.activeTab.uid === tabValue) {
                panel.activeTab = panel.tabs[0]
            }
        }
        emit("remove-tab", tabValue)
    }

    defineExpose({
        panels,
        openTabs,
        focusTab,
        setTabValue,
        saveState,
        splitOrientation,
    })
</script>

<style lang="scss" scoped>
    .main-editor{
        display: grid;
        grid-template-rows: auto 1fr;
        height: 100%;

        .editor-wrapper {
            position: relative;
            height: 100%;
        }
    }

    .tabs-actions {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        flex-shrink: 0;
    }

    :deep(.editor-panels){
        position: absolute;
    }
    :deep(.kel-splitter-bar){
        width: 2px !important;
    }

    .default-theme{
        :deep(.kel-splitter-panel) {
            background-color: var(--ks-bg-surface);
        }

        :deep(.kel-splitter__splitter){
            border-top-color: var(--ks-border-default);
            background-color: var(--ks-bg-surface);
            &:before, &:after{
                background-color: var(--ks-text-secondary);
            }
        }
    }
</style>
