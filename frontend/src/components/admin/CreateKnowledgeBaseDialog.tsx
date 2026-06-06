import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";

import { createKnowledgeBase } from "@/services/knowledgeService";
import { getSystemSettings, type ModelCandidate } from "@/services/settingsService";
import { getAiModelConfigs, type AiModelConfigItem } from "@/services/aiConfigService";
import { getErrorMessage } from "@/utils/error";
import { storage } from "@/utils/storage";

const formSchema = z.object({
  name: z.string().min(1, "请输入知识库名称").max(50, "名称不能超过50个字符"),
  embeddingModel: z.string().min(1, "请选择Embedding模型"),
  collectionName: z
    .string()
    .min(1, "请输入Collection名称")
    .max(50, "名称不能超过50个字符")
    .regex(/^[a-z0-9]+$/, "只能包含小写英文字母和数字"),
});

type FormValues = z.infer<typeof formSchema>;

const EMBEDDING_MODEL_CONFIG_KEY = "ai.embedding.model";
const MODEL_REGISTRY_CONFIG_KEY = "ai.models";

interface TenantModelRegistryItem {
  id?: string;
  provider?: string;
  model?: string;
  capability?: string;
  enabled?: boolean;
  dimension?: number | null;
  priority?: number | null;
}

function currentTenantId() {
  const user = storage.getUser() as ({ tenantId?: string } | null);
  return user?.tenantId?.trim() || "default";
}

function parseModelRegistry(value?: string | null): TenantModelRegistryItem[] {
  if (!value?.trim()) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function resolveEmbeddingModelCandidates(
  candidates: ModelCandidate[] = [],
  configs: AiModelConfigItem[] = [],
  defaultModel?: string | null,
  tenantId = "default"
): ModelCandidate[] {
  const uniqueMap = new Map<string, ModelCandidate>();

  candidates.forEach((item) => {
    if (!item || item.enabled === false) return;
    const id = (item.id || item.model || "").trim();
    if (!id) return;
    uniqueMap.set(id, { ...item, id });
  });

  configs
    .filter((item) => item.configKey === MODEL_REGISTRY_CONFIG_KEY)
    .filter((item) => !item.tenantId || item.tenantId === tenantId)
    .flatMap((item) => parseModelRegistry(item.configValue))
    .filter((item) => item.enabled !== false)
    .filter((item) => item.capability === "embedding")
    .forEach((item) => {
      const id = (item.id || item.model || "").trim();
      if (!id || uniqueMap.has(id)) return;
      uniqueMap.set(id, {
        id,
        provider: item.provider || "",
        model: item.model || id,
        enabled: true,
        dimension: item.dimension,
        priority: item.priority
      });
    });

  const addFallbackModel = (model?: string | null) => {
    const normalized = (model || "").trim();
    if (!normalized) return;
    const exists = Array.from(uniqueMap.values()).some((item) => {
      return item.id === normalized || item.model === normalized;
    });
    if (exists) return;
    uniqueMap.set(normalized, {
      id: normalized,
      provider: "",
      model: normalized,
      enabled: true
    });
  };

  addFallbackModel(defaultModel);
  addFallbackModel(
    configs.find((item) => item.configKey === EMBEDDING_MODEL_CONFIG_KEY)?.configValue
  );

  return Array.from(uniqueMap.values());
}

interface CreateKnowledgeBaseDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function CreateKnowledgeBaseDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateKnowledgeBaseDialogProps) {
  const [loading, setLoading] = useState(false);
  const [modelLoading, setModelLoading] = useState(false);
  const [embeddingModels, setEmbeddingModels] = useState<ModelCandidate[]>([]);

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
      embeddingModel: "",
      collectionName: "",
    },
  });

  useEffect(() => {
    if (!open) return;
    let active = true;
    setModelLoading(true);
    const tenantId = currentTenantId();
    Promise.allSettled([getSystemSettings(), getAiModelConfigs({ tenantId })])
      .then(([settingsResult, configsResult]) => {
        if (!active) return;
        const embeddingSettings =
          settingsResult.status === "fulfilled" ? settingsResult.value.ai?.embedding : undefined;
        const configs = configsResult.status === "fulfilled" ? configsResult.value : [];
        setEmbeddingModels(
          resolveEmbeddingModelCandidates(
            embeddingSettings?.candidates || [],
            configs,
            embeddingSettings?.defaultModel,
            tenantId
          )
        );
      })
      .finally(() => {
        if (active) {
          setModelLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [open, form]);

  const selectOptions = useMemo(() => {
    if (embeddingModels.length === 0) return [];
    const uniqueMap = new Map<string, ModelCandidate>();
    embeddingModels.forEach((item) => {
      if (item.id) {
        uniqueMap.set(item.id, item);
      }
    });
    return Array.from(uniqueMap.values());
  }, [embeddingModels]);

  const onSubmit = async (values: FormValues) => {
    try {
      setLoading(true);
      await createKnowledgeBase(values);
      toast.success("创建成功");
      form.reset();
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "创建失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDialogOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      form.reset({
        name: "",
        embeddingModel: "",
        collectionName: "",
      });
    }
    onOpenChange(nextOpen);
  };

  return (
    <Dialog open={open} onOpenChange={handleDialogOpenChange}>
      <DialogContent
        className="sm:max-w-[500px]"
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>创建知识库</DialogTitle>
          <DialogDescription>
            创建一个新的知识库，用于存储和检索文档
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>知识库名称</FormLabel>
                  <FormControl>
                    <Input placeholder="例如：产品文档库" {...field} />
                  </FormControl>
                  <FormDescription>
                    为知识库起一个易于识别的名称
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="embeddingModel"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Embedding模型</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="选择Embedding模型" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {modelLoading ? (
                        <SelectItem value="loading" disabled>
                          加载中...
                        </SelectItem>
                      ) : selectOptions.length === 0 ? (
                        <SelectItem value="empty" disabled>
                          暂无可用模型
                        </SelectItem>
                      ) : (
                        selectOptions.map((item) => {
                          const label = item.provider && item.model
                            ? `${item.provider} · ${item.model}`
                            : item.model || item.id;
                          return (
                            <SelectItem key={item.id} value={item.id}>
                              {label}
                            </SelectItem>
                          );
                        })
                      )}
                    </SelectContent>
                  </Select>
                  <FormDescription>
                    选择用于向量化文档的模型
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="collectionName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Collection名称</FormLabel>
                  <FormControl>
                    <Input placeholder="例如：productdocs" {...field} />
                  </FormControl>
                  <FormDescription>
                    只能包含小写英文字母和数字
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => handleDialogOpenChange(false)}
                disabled={loading}
              >
                取消
              </Button>
              <Button type="submit" disabled={loading}>
                {loading ? "创建中..." : "创建"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
