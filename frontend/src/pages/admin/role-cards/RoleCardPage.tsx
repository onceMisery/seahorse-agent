import { useEffect, useState } from "react";
import { Pencil, Plus, RefreshCw, ShieldAlert, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  activateRoleCard,
  createRoleCard,
  deleteRoleCard,
  listRoleCards,
  updateRoleCard,
  type RoleCardRequest,
  type RoleCardVO
} from "@/services/roleCardService";
import { getErrorMessage } from "@/utils/error";

type RoleCardFormState = {
  id?: number | string;
  name: string;
  definition: string;
  higherPerm: boolean;
  shareScope: string;
  approvalStatus: string;
  published: boolean;
};

const EMPTY_FORM: RoleCardFormState = {
  name: "",
  definition: "",
  higherPerm: false,
  shareScope: "PRIVATE",
  approvalStatus: "PENDING",
  published: false
};

function truthy(value: unknown) {
  return value === true || value === 1;
}

function textOrDash(value: unknown) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function toForm(card: RoleCardVO): RoleCardFormState {
  return {
    id: card.id,
    name: card.name || "",
    definition: card.definition || "",
    higherPerm: truthy(card.higherPerm),
    shareScope: card.shareScope || "PRIVATE",
    approvalStatus: card.approvalStatus || "PENDING",
    published: truthy(card.published)
  };
}

function toRequest(form: RoleCardFormState): RoleCardRequest {
  return {
    name: form.name.trim(),
    definition: form.definition.trim(),
    higherPerm: form.higherPerm,
    avatarRef: null,
    shareScope: form.shareScope,
    approvalStatus: form.approvalStatus,
    published: form.published
  };
}

export function RoleCardPage() {
  const [cards, setCards] = useState<RoleCardVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [operatingId, setOperatingId] = useState<number | string | null>(null);
  const [form, setForm] = useState<RoleCardFormState | null>(null);
  const [saving, setSaving] = useState(false);

  const loadCards = async () => {
    try {
      setLoading(true);
      setCards(await listRoleCards());
    } catch (error) {
      toast.error(getErrorMessage(error, "加载角色卡失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCards();
  }, []);

  const handleSave = async () => {
    if (!form) return;
    const request = toRequest(form);
    if (!request.name) {
      toast.error("请输入角色卡名称");
      return;
    }
    if (!request.definition) {
      toast.error("请输入角色卡定义");
      return;
    }

    try {
      setSaving(true);
      if (form.id) {
        await updateRoleCard(form.id, request);
      } else {
        await createRoleCard(request);
      }
      toast.success("角色卡已保存");
      setForm(null);
      await loadCards();
    } catch (error) {
      toast.error(getErrorMessage(error, "保存角色卡失败"));
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  const handleActivate = async (id: number | string) => {
    try {
      setOperatingId(id);
      await activateRoleCard(id);
      toast.success("角色卡已启用");
      await loadCards();
    } catch (error) {
      toast.error(getErrorMessage(error, "启用角色卡失败"));
      console.error(error);
    } finally {
      setOperatingId(null);
    }
  };

  const handleDelete = async (id: number | string) => {
    if (!confirm("确认删除此角色卡？")) return;
    try {
      setOperatingId(id);
      await deleteRoleCard(id);
      toast.success("角色卡已删除");
      await loadCards();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除角色卡失败"));
      console.error(error);
    } finally {
      setOperatingId(null);
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">角色卡</h1>
          <p className="admin-page-subtitle">管理可复用角色定义、共享范围、发布状态和高权限标识</p>
        </div>
        <div className="admin-page-actions">
          <Button onClick={() => setForm({ ...EMPTY_FORM })}>
            <Plus className="mr-1 h-4 w-4" />
            新建角色卡
          </Button>
          <Button variant="outline" onClick={loadCards} disabled={loading}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      {form ? (
        <Card>
          <CardContent className="grid gap-4 pt-6 md:grid-cols-2">
            <label htmlFor="role-card-name" className="space-y-2 text-sm font-medium text-slate-700">
              <span>名称</span>
              <Input
                id="role-card-name"
                value={form.name}
                onChange={(event) => setForm((prev) => prev ? { ...prev, name: event.target.value } : prev)}
              />
            </label>
            <label htmlFor="role-card-share-scope" className="space-y-2 text-sm font-medium text-slate-700">
              <span>共享范围</span>
              <select
                id="role-card-share-scope"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={form.shareScope}
                onChange={(event) => setForm((prev) => prev ? { ...prev, shareScope: event.target.value } : prev)}
              >
                <option value="PRIVATE">PRIVATE</option>
                <option value="TEAM">TEAM</option>
                <option value="ORG">ORG</option>
              </select>
            </label>
            <label htmlFor="role-card-approval-status" className="space-y-2 text-sm font-medium text-slate-700">
              <span>审批状态</span>
              <select
                id="role-card-approval-status"
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={form.approvalStatus}
                onChange={(event) => setForm((prev) => prev ? { ...prev, approvalStatus: event.target.value } : prev)}
              >
                <option value="PENDING">PENDING</option>
                <option value="APPROVED">APPROVED</option>
                <option value="REJECTED">REJECTED</option>
              </select>
            </label>
            <div className="flex items-end gap-6">
              <label htmlFor="role-card-higher-perm" className="flex items-center gap-2 text-sm font-medium text-slate-700">
                <input
                  id="role-card-higher-perm"
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300"
                  checked={form.higherPerm}
                  onChange={(event) => setForm((prev) => prev ? { ...prev, higherPerm: event.target.checked } : prev)}
                />
                <span>高权限</span>
              </label>
              <label htmlFor="role-card-published" className="flex items-center gap-2 text-sm font-medium text-slate-700">
                <input
                  id="role-card-published"
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300"
                  checked={form.published}
                  onChange={(event) => setForm((prev) => prev ? { ...prev, published: event.target.checked } : prev)}
                />
                <span>发布</span>
              </label>
            </div>
            <label htmlFor="role-card-definition" className="space-y-2 text-sm font-medium text-slate-700 md:col-span-2">
              <span>定义</span>
              <textarea
                id="role-card-definition"
                className="min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={form.definition}
                onChange={(event) => setForm((prev) => prev ? { ...prev, definition: event.target.value } : prev)}
              />
            </label>
            <div className="flex gap-2 md:col-span-2">
              <Button onClick={handleSave} disabled={saving}>
                保存角色卡
              </Button>
              <Button variant="outline" onClick={() => setForm(null)} disabled={saving}>
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : cards.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">暂无角色卡</div>
          ) : (
            <Table className="min-w-[880px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[180px]">名称</TableHead>
                  <TableHead>定义</TableHead>
                  <TableHead className="w-[120px]">共享范围</TableHead>
                  <TableHead className="w-[130px]">审批状态</TableHead>
                  <TableHead className="w-[120px]">治理</TableHead>
                  <TableHead className="w-[110px]">状态</TableHead>
                  <TableHead className="w-[190px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cards.map((card) => (
                  <TableRow key={card.id}>
                    <TableCell>
                      <div className="font-medium text-slate-900">{card.name}</div>
                      <div className="mt-1 text-xs text-muted-foreground">ID: {card.id}</div>
                    </TableCell>
                    <TableCell className="max-w-[320px] whitespace-pre-wrap text-muted-foreground">
                      {textOrDash(card.definition)}
                    </TableCell>
                    <TableCell>{textOrDash(card.shareScope)}</TableCell>
                    <TableCell>{textOrDash(card.approvalStatus)}</TableCell>
                    <TableCell>
                      {truthy(card.higherPerm) ? (
                        <Badge variant="destructive">
                          <ShieldAlert className="mr-1 h-3 w-3" />
                          高权限
                        </Badge>
                      ) : (
                        <Badge variant="secondary">普通</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      {truthy(card.enabled) ? (
                        <Badge className="bg-green-100 text-green-700">启用</Badge>
                      ) : (
                        <Badge variant="secondary">未启用</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {!truthy(card.enabled) ? (
                          <Button
                            variant="outline"
                            size="sm"
                            aria-label={`启用-${card.id}`}
                            disabled={operatingId === card.id}
                            onClick={() => handleActivate(card.id)}
                          >
                            启用
                          </Button>
                        ) : null}
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={operatingId === card.id}
                          onClick={() => setForm(toForm(card))}
                        >
                          <Pencil className="mr-1 h-4 w-4" />
                          编辑
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          disabled={operatingId === card.id}
                          onClick={() => handleDelete(card.id)}
                        >
                          <Trash2 className="mr-1 h-4 w-4" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
