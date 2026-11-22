import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const allowedModels = new Set(["openai/gpt-oss-20b", "llama-3.3-70b-versatile"]);

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const apiKey = Deno.env.get("GROQ_API_KEY");
  if (!apiKey) return json({
    error: "O servidor ainda não recebeu a chave da IA. Configure GROQ_API_KEY nos secrets do Supabase.",
    code: "AI_PROVIDER_NOT_CONFIGURED",
  }, 200);

  try {
    const body = await req.json();
    if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 20) {
      return json({ error: "Histórico de mensagens inválido." }, 400);
    }
    const messages = body.messages
      .filter((m: any) => m && ["system", "user", "assistant"].includes(m.role) && typeof m.content === "string")
      .map((m: any) => ({ role: m.role, content: m.content.slice(0, 12000) }));
    if (!messages.length || messages[messages.length - 1].role !== "user") {
      return json({ error: "A última mensagem precisa ser do estudante." }, 400);
    }
    const requestedModel = typeof body.model === "string" ? body.model : "openai/gpt-oss-20b";
    const model = allowedModels.has(requestedModel) ? requestedModel : "openai/gpt-oss-20b";
    const upstream = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model, messages, temperature: 0.65, max_tokens: 1800 }),
    });
    const result = await upstream.json();
    return json(result, upstream.ok ? 200 : 200);
  } catch (_error) {
    return json({ error: "Não foi possível processar a solicitação." }, 200);
  }
});
function json(value: unknown, status: number) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}