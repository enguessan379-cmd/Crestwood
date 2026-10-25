#include "CharacterCreator.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>

#include "../main.h"
#include "../game/game.h"
#include "../game/playerped.h"
#include "../game/Camera.h"
#include "../game/RW/rwcore.h"
#include "../game/RW/rpworld.h"
#include "../net/netgame.h"
#include "../java/jniutil.h"
#include "ClothesCustomization.h"
#include "gui.h"
#include "../vendor/imgui/imgui.h"

extern CNetGame* pNetGame;
extern CJavaWrapper* pJavaWrapper;
extern CGame* pGame;
extern UI* pUI;

namespace {

	// Mantém o personagem de frente pra câmera (visual mugshot) enquanto as
	// janelas de criação estiverem abertas. Usa a mesma convenção de rotação
	// do jogo usada pela câmera do celular: heading = atan2(-dx, dy).
	uint32_t g_lastFaceCameraTick = 0;
	uint32_t g_lastFallGuardTick = 0;

	// Janela de correção da pose de queda presa na tela de SELEÇÃO (ela abre
	// no mesmo instante do spawn, quando o boneco ainda está assentando). O
	// reset do modelo é repetido 1x ~600ms depois, caso o primeiro rode antes
	// do ped terminar de cair no chão.
	uint32_t g_selectOpenTick = 0;
	int g_selectModelRetries = 0;

	// JSON bruto recebido do servidor + flag de setup pendente. A OpenSelect
	// roda na THREAD DE REDE no mesmo instante em que o cliente ainda está
	// spawnando o ped — mexer no modelo/texturas do boneco a partir dela
	// disputa com a thread do jogo (que está criando/streamando o modelo) e
	// crasha. Por isso ela só guarda os dados aqui e o Render (thread do
	// jogo, o mesmo lugar onde o "+" troca o modelo sem crash) executa o
	// setup no primeiro tick.
	std::string g_selectJson;
	uint32_t g_autoSpawnTick = 0; // spawn direct : repli vers la selection si le serveur ne repond pas
	bool g_selectSetupPending = false;

	// MODE TEST : panneau de vêtements ouvert en local par le bouton du HUD,
	// sans aucun échange avec le serveur (donc sans Pawn). Rien n'est envoyé ni
	// sauvegardé : « FERMER » referme simplement le panneau.
	std::atomic<bool> g_testOpenRequested{false};
	bool g_testMode = false;

	// Se o boneco fugir do ponto de criação (o servidor congelou, mas em casos
	// de reconexão/streaming a área demora a carregar), devolve ele pro lugar
	// SEM teleporte — escreve a matriz direto, igual o keep-dead da tela de
	// morte. Teleporte (SetPlayerPos) num boneco congelado reinicia a tarefa
	// de queda no cliente (o "abraça o ar"), por isso a correção é manual.
	constexpr float kCreateX = 1516.0f;
	constexpr float kCreateY = -1030.0f;
	constexpr float kCreateZ = 25.6f;

	// Remove a tarefa de queda presa. O spawn do cliente solta o ped ~0.5m
	// acima do chão e o jogo inicia a queda; se o servidor congela nesse meio
	// tempo (seleção abre no mesmo instante do spawn), a pose de "caindo no
	// lugar" fica ativa. Limpar as tarefas do ped quando ele já está na altura
	// do chão faz o jogo reavaliar e ficar em pé — o mesmo efeito que o "+"
	// tem na criação ao trocar o modelo do boneco.
	bool ClearStuckFall()
	{
		CPlayerPed* ped = pGame ? pGame->FindPlayerPed() : nullptr;
		if (!ped || !ped->m_pPed || !ped->m_pPed->IsAdded()) return false;
		if (ped->IsInVehicle()) return false;
		if (!ped->m_pPed->m_pIntelligence) return false;

		ped->m_pPed->m_pIntelligence->m_TaskMgr.FlushImmediately();
		return true;
	}

	// Recria o modelo atual do ped local chamando o SetModelIndex REAL do jogo
	// (via CPlayerPed — o stub de CEntityGTA não entra nesse caminho). É o
	// mesmo reset que limpa a pose de queda presa quando o jogador troca de
	// sexo na criação ("+"). O servidor congela o boneco enquanto o spawn do
	// cliente ainda o solta ~0.5m acima do chão; essa tarefa de queda não se
	// resolve sozinha e só a re-inicialização do modelo faz o jogo reavaliar e
	// deixar o boneco em pé.
	bool ReapplyPedModel()
	{
		CPlayerPed* ped = pGame ? pGame->FindPlayerPed() : nullptr;
		if (!ped || !ped->m_pPed || !ped->m_pPed->IsAdded()) return false;
		if (ped->IsInVehicle()) return false;
		const uint32_t model = ped->m_pPed->m_nModelIndex;
		if (model == 0 || model > 20000) return false;
		ped->SetModelIndex(model);
		return true;
	}

	void FallGuard()
	{
		const uint32_t now = GetTickCount();
		if (now - g_lastFallGuardTick < 400) return;
		g_lastFallGuardTick = now;

		CPlayerPed* ped = pGame ? pGame->FindPlayerPed() : nullptr;
		if (!ped || !ped->m_pPed || !ped->m_pPed->IsAdded()) return;
		if (ped->IsInVehicle()) return;

		const CVector& pos = ped->m_pPed->GetPosition();
		const float dx = pos.x - kCreateX;
		const float dy = pos.y - kCreateY;
		const float dz = pos.z - kCreateZ;
		// Fugiu de verdade (caiu/foi empurrado): devolve pro ponto de criação
		// sem teleporte. Dentro da margem, não mexe em nada.
		if (dx * dx + dy * dy > 4.0f || dz > 1.5f || dz < -1.5f)
		{
			RwMatrix matrix = ped->m_pPed->GetMatrix().ToRwMatrix();
			matrix.pos.x = kCreateX;
			matrix.pos.y = kCreateY;
			matrix.pos.z = kCreateZ;
			ped->m_pPed->SetMatrix((CMatrix&)matrix);
			ped->m_pPed->UpdateRW();
			ped->m_pPed->UpdateRwFrame();
		}
	}

	void FacePlayerToCamera()
	{
		CPlayerPed* ped = pGame ? pGame->FindPlayerPed() : nullptr;
		if (!ped || !ped->m_pPed) return;

		const CVector cameraPos = CCamera::GetActiveCamera().Source;
		const CVector pedPos = ped->m_pPed->GetPosition();

		CVector dir = cameraPos - pedPos;
		dir.z = 0.0f;
		if (dir.Magnitude2D() < 0.05f) return;	float rotation = std::atan2(-dir.x, dir.y) * 57.295776f;
	if (rotation < 0.0f) rotation += 360.0f;
	if (rotation >= 360.0f) rotation -= 360.0f;
	ped->SetTargetRotation(rotation);
}

	enum class State
	{
		Hidden,
		Select,        // SELECIONE O PERSONAGEM
		NewCharModal,  // NOVO PERSONAGEM
		Creating,      // CRIAR PERSONAGEM
		Waiting        // aguardando resposta do servidor
	};

	State g_state = State::Hidden;
	bool g_hasChar = false;
	std::string g_charName;

	// Dinheiro/Banco do personagem exibidos na tela de seleção (chegam no
	// JSON do servidor).
	int g_cash = 0;
	int g_bank = 0;

	// Conta SEM personagem: o boneco fica invisível na seleção até apertar
	// "+" (começar a criar). Com personagem salvo, a seleção mostra o boneco
	// normal. A flag é só estado — quem aplica no ped é o tick de 300ms do
	// Render (thread do jogo) e os cliques ("+", personagem, NASCER,
	// CANCELAR). Nada disso roda na thread de rede.
	bool g_pedHidden = false;

	RpAtomic* SetAtomicRenderCB(RpAtomic* atomic, void* data)
	{
		if (!atomic) return atomic;
		const bool visible = *static_cast<bool*>(data);
		if (visible)
			rwObjectSetFlags(atomic, rwObjectGetFlags(atomic) | rpATOMICRENDER);
		else
			rwObjectSetFlags(atomic, rwObjectGetFlags(atomic) & ~rpATOMICRENDER);
		return atomic;
	}

	void SetPedVisible(bool visible)
	{
		CPlayerPed* ped = pGame ? pGame->FindPlayerPed() : nullptr;
		if (!ped || !ped->m_pPed) return;
		ped->m_pPed->m_bIsVisible = visible;
		// No engine Android o m_bIsVisible sozinho não tira o ped LOCAL da
		// renderização em 3ª pessoa. O caminho comprovado é o flag de render
		// de cada atomic do clump (rpATOMICRENDER) — o mesmo que o jogo usa
		// pra pular atomics na lista visível. Reaplicado todo frame, então
		// qualquer recriação de modelo (PreviewGender/ReapplyPedModel) que
		// resete o clump volta a ser escondida no frame seguinte.
		RpClump* clump = ped->m_pPed->m_pRwClump;
		if (clump)
		{
			RpClumpForAllAtomics(clump, SetAtomicRenderCB, &visible);
		}
	}

	// Campos do modal
	int g_gender = 0; // 0 = masculino, 1 = feminino
	char g_nameBuf[64] = "";
	char g_lastNameBuf[64] = "";
	int g_day = 0;
	int g_month = 0;
	int g_year = 0;

	// Campo sendo editado pelo teclado normal do launcher
	enum class EditTarget
	{
		None,
		Name,
		LastName,
		Birth
	};
	EditTarget g_editTarget = EditTarget::None;

	// Widget receptor do teclado do launcher (o mesmo usado pelo chat).
	// O texto digitado no teclado Android volta inteiro no keyboardEvent.
	class CreatorKeyboardHost : public Widget
	{
	public:
		void keyboardEvent(const std::string& input) override;
		void keyboardCancelEvent();
	};

	CreatorKeyboardHost g_kbHost;

	int JsonInt(const std::string& json, const char* key, int def)
	{
		std::string needle = std::string("\"") + key + "\"";
		const char* p = strstr(json.c_str(), needle.c_str());
		if (!p) return def;
		p += needle.size();
		int value = def;
		if (sscanf(p, ":%d", &value) == 1) return value;
		return def;
	}

	bool JsonBool(const std::string& json, const char* key, bool def)
	{
		std::string needle = std::string("\"") + key + "\"";
		const char* p = strstr(json.c_str(), needle.c_str());
		if (!p) return def;
		p += needle.size();
		int value = def ? 1 : 0;
		if (sscanf(p, ":%d", &value) == 1) return value != 0;
		if (strncmp(p, ":\"true\"", 7) == 0 || strncmp(p, ":true", 5) == 0) return true;
		if (strncmp(p, ":\"false\"", 8) == 0 || strncmp(p, ":false", 6) == 0) return false;
		return def;
	}

	std::string JsonString(const std::string& json, const char* key, const std::string& def)
	{
		std::string needle = std::string("\"") + key + "\"";
		const char* p = strstr(json.c_str(), needle.c_str());
		if (!p) return def;
		p += needle.size();
		while (*p && (*p == ' ' || *p == ':' || *p == '"')) ++p;
		std::string out;
		while (*p && *p != '"') {
			out += *p++;
		}
		return out.empty() ? def : out;
	}

	void ApplyLookFromJson(const std::string& json)
	{
		ClothesLook& look = ClothesCustomization::Look();
		look.skinTone     = static_cast<uint8_t>(JsonInt(json, "skinTone", 1));
		look.face         = static_cast<uint8_t>(JsonInt(json, "face", 1));
		look.hairStyle    = static_cast<uint8_t>(JsonInt(json, "hairStyle", 1));
		look.hairColor    = static_cast<uint8_t>(JsonInt(json, "hairColor", 1));
		look.eyebrowStyle = static_cast<uint8_t>(JsonInt(json, "eyebrowStyle", 0));
		look.eyebrowColor = static_cast<uint8_t>(JsonInt(json, "eyebrowColor", 1));
		look.beardStyle   = static_cast<uint8_t>(JsonInt(json, "beardStyle", 0));
		look.beardColor   = static_cast<uint8_t>(JsonInt(json, "beardColor", 1));
		look.makeupStyle  = static_cast<uint8_t>(JsonInt(json, "makeupStyle", 0));
		look.makeupColor  = static_cast<uint8_t>(JsonInt(json, "makeupColor", 1));
		look.topStyle     = static_cast<uint8_t>(JsonInt(json, "topStyle", 0));
		look.topColor     = static_cast<uint8_t>(JsonInt(json, "topColor", 1));
		look.bottomStyle  = static_cast<uint8_t>(JsonInt(json, "bottomStyle", 1));
		look.bottomColor  = static_cast<uint8_t>(JsonInt(json, "bottomColor", 1));
		look.shoesStyle   = static_cast<uint8_t>(JsonInt(json, "shoesStyle", 0));
		look.shoesColor   = static_cast<uint8_t>(JsonInt(json, "shoesColor", 1));
		look.tattooArmLeft  = static_cast<uint8_t>(JsonInt(json, "tattooArmLeft", 0));
		look.tattooArmRight = static_cast<uint8_t>(JsonInt(json, "tattooArmRight", 0));
		look.tattooLegLeft  = static_cast<uint8_t>(JsonInt(json, "tattooLegLeft", 0));
		look.tattooLegRight = static_cast<uint8_t>(JsonInt(json, "tattooLegRight", 0));
		look.tattooBack     = static_cast<uint8_t>(JsonInt(json, "tattooBack", 0));
		look.tattooFront    = static_cast<uint8_t>(JsonInt(json, "tattooFront", 0));
		look.policeBelt = static_cast<uint8_t>(JsonInt(json, "policeBelt", 0));
		look.policeVest = static_cast<uint8_t>(JsonInt(json, "policeVest", 0));
		look.policeHat  = static_cast<uint8_t>(JsonInt(json, "policeHat", 0));
		ClothesCustomization::ApplyCurrent();
	}

	// Troca o modelo do ped local para o do gênero escolhido (preview ao vivo).
	// 163 = masculino (bmyboun), 9 = feminino (bfori) — os skins suportados
	// pelo sistema de clothes.
	void PreviewGender(int gender)
	{
		ClothesCustomization::Reset(gender);
		if (pGame) {
			if (CPlayerPed* ped = pGame->FindPlayerPed()) {
				ped->SetModelIndex(gender == 1 ? 9 : 163);
			}
		}
		ClothesCustomization::ApplyCurrent();
	}

	// MODE TEST : ouvre directement le panneau de vêtements, sans serveur.
	// Exécuté uniquement depuis Render() (thread du jeu).
	void OpenTestNow()
	{
		g_testMode = true;
		g_selectModelRetries = 0;
		g_selectSetupPending = false;
		g_editTarget = EditTarget::None;
		g_pedHidden = false;
		SetPedVisible(true);
		ClearStuckFall();
		PreviewGender(g_gender);
		g_state = State::Creating;
	}

	// Abre o teclado normal do launcher (teclado Android do sistema) para
	// preencher o campo indicado.
	void BeginEdit(EditTarget target)
	{
		g_editTarget = target;
		if (pUI && pUI->keyboard()) {
			pUI->keyboard()->show(&g_kbHost);
		}
	}

	void CommitEdit(const std::string& input)
	{
		const EditTarget target = g_editTarget;
		g_editTarget = EditTarget::None;

		if (target == EditTarget::Name) {
			const size_t len = std::min(input.size(), sizeof(g_nameBuf) - 1);
			memcpy(g_nameBuf, input.c_str(), len);
			g_nameBuf[len] = 0;
		}
		else if (target == EditTarget::LastName) {
			const size_t len = std::min(input.size(), sizeof(g_lastNameBuf) - 1);
			memcpy(g_lastNameBuf, input.c_str(), len);
			g_lastNameBuf[len] = 0;
		}
		else if (target == EditTarget::Birth) {
			// Formato aceito: dd/mm/aaaa (ou ddmmaaaa, d/m/aaaa etc.)
			int parts[3] = {0, 0, 0};
			int count = 0;
			const char* p = input.c_str();
			while (*p && count < 3) {
				if (*p >= '0' && *p <= '9') {
					int v = 0;
					while (*p >= '0' && *p <= '9' && v < 10000) {
						v = v * 10 + (*p - '0');
						++p;
					}
					parts[count++] = v;
				}
				else {
					++p;
				}
			}
			if (count == 3) {
				g_day = parts[0];
				g_month = parts[1];
				g_year = parts[2];
			}
		}
	}

	// Mostra no botão do campo o que está sendo digitado (o launcher manda
	// cada letra do teclado Android via updateKeyboardInput).
	std::string FieldLiveText()
	{
		if (pUI && pUI->keyboard() && pUI->keyboard()->input()) {
			return pUI->keyboard()->input()->inputString();
		}
		return std::string();
	}

	// Inicia uma janela da criação com a fonte pequena (PushFont) e
	// dimensiona/centraliza na tela. Deve terminar com CreatorEnd().
	bool CreatorBegin(const char* title, float width, float height, ImGuiWindowFlags extraFlags = 0)
	{
		const ImVec2 display = ImGui::GetIO().DisplaySize;
		float w = width;
		float h = height;
		if (w > display.x - 20.0f) w = display.x - 20.0f;
		if (h > display.y - 20.0f) h = display.y - 20.0f;
		if (w < 280.0f) w = 280.0f;

		ImGui::SetNextWindowPos(ImVec2((display.x - w) * 0.5f, (display.y - h) * 0.5f), ImGuiCond_Always);
		ImGui::SetNextWindowSize(ImVec2(w, h), ImGuiCond_Always);

		const bool open = ImGui::Begin(title, nullptr,
				ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoSavedSettings | extraFlags);
		if (open) {
			// Fonte compacta: a fonte global do launcher é gigante (escala com
			// a resolução). Tudo dentro da janela usa a fonte pequena.
			if (pUI && pUI->fontSmall()) {
				ImGui::PushFont(pUI->fontSmall());
			}
		}
		return open;
	}

	void CreatorEnd()
	{
		if (pUI && pUI->fontSmall()) {
			ImGui::PopFont();
		}
		ImGui::End();
	}

	void RenderSelectWindow()
	{
		const float winW = 760.0f;
		const float winH = 620.0f;
		if (!CreatorBegin("SÉLECTION DU PERSONNAGE", winW, winH)) {
			ImGui::End();
			return;
		}

		ImGui::Text("EMPLACEMENTS UTILISÉS  %s", g_hasChar ? "1/1" : "0/1");
		ImGui::Separator();
		ImGui::Spacing();

		// Corpo rolável (grade de slots + status); botões fixos no rodapé.
		ImGui::BeginChild("select_body", ImVec2(0.0f, -90.0f), false);

		const float slotW = 150.0f;
		const float slotH = 110.0f;

		ImGui::BeginChild("slots", ImVec2(slotW * 2.0f + 10.0f, 0.0f), true);
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 2; ++col) {
				const int slot = row * 2 + col;
				ImGui::PushID(slot);
				if (col > 0) ImGui::SameLine(0.0f, 10.0f);
				if (slot == 0) {
					if (g_hasChar) {
						if (ImGui::Button(g_charName.c_str(), ImVec2(slotW, slotH))) {
							// Clicou no personagem: mostra o boneco com o visual salvo.
							g_pedHidden = false;
							SetPedVisible(true);
							ApplyLookFromJson(g_selectJson);
						}
					}
					else {
						ImGui::PushID("plus");
						if (ImGui::Button("+", ImVec2(slotW, slotH))) {
							g_state = State::NewCharModal;
							g_gender = 0;
							g_nameBuf[0] = 0;
							g_lastNameBuf[0] = 0;
							g_day = g_month = g_year = 0;
							g_editTarget = EditTarget::None;
							// O "+" mostra o boneco (preview do gênero no modal).
							g_pedHidden = false;
							SetPedVisible(true);
							PreviewGender(0);
						}
						ImGui::PopID();
					}
				}
				else {
					ImGui::PushID("lock");
					ImGui::Button("VERROUILLÉ", ImVec2(slotW, slotH));
					ImGui::PopID();
				}
				ImGui::PopID();
			}
			ImGui::NewLine();
		}
		ImGui::EndChild();

		// Status do personagem
		ImGui::SameLine();
		ImGui::BeginChild("status", ImVec2(0.0f, 0.0f), true);
		ImGui::Text("STATUT DU PERSONNAGE");
		ImGui::Separator();
		ImGui::Spacing();
		ImGui::Text("DATE DE NAISSANCE");
		if (g_hasChar && g_day > 0 && g_month > 0 && g_year > 0) {
			ImGui::Text("%02d/%02d/%04d", g_day, g_month, g_year);
		}
		else {
			ImGui::Text("--");
		}
		ImGui::Spacing();
		ImGui::Text("ARGENT");
		{
			char buf[32];
			std::snprintf(buf, sizeof(buf), "$%d", g_cash);
			ImGui::Text("%s", g_hasChar ? buf : "--");
		}
		ImGui::Spacing();
		ImGui::Text("BANQUE");
		{
			char buf[32];
			std::snprintf(buf, sizeof(buf), "$%d", g_bank);
			ImGui::Text("%s", g_hasChar ? buf : "--");
		}
		ImGui::EndChild();

		ImGui::EndChild(); // select_body

		ImGui::Spacing();

		const float btnW = winW - ImGui::GetStyle().WindowPadding.x * 2.0f;
		if (g_hasChar && ImGui::Button("ENTRER EN JEU", ImVec2(btnW, 46.0f))) {
			g_pedHidden = false;
			SetPedVisible(true);
			g_state = State::Waiting;
			CharacterCreator::SendSpawnRequest();
		}

		CreatorEnd();
	}

		bool ModernBegin(const char* title)
		{
			const ImVec2 display = ImGui::GetIO().DisplaySize;
			ImGui::SetNextWindowPos(ImVec2(0.0f, 0.0f), ImGuiCond_Always);
			ImGui::SetNextWindowSize(display, ImGuiCond_Always);
			ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0.0f, 0.0f));
			ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 0.0f);
			ImGui::PushStyleVar(ImGuiStyleVar_WindowBorderSize, 0.0f);
			ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0.0f, 0.0f, 0.0f, 0.0f)); // plein ecran transparent : le personnage reste visible
			const bool open = ImGui::Begin(title, nullptr,
				ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoSavedSettings);
			if (open) {
				// fond sombre uniquement derriere le panneau de gauche
				const float panelBgW = std::min(560.0f, display.x * 0.46f) + 34.0f;
				ImGui::GetWindowDrawList()->AddRectFilled(ImVec2(0.0f, 0.0f), ImVec2(panelBgW, display.y),
					IM_COL32(2, 4, 4, 215));
			}
			if (open && pUI && pUI->fontSmall()) ImGui::PushFont(pUI->fontSmall());
			return open;
		}

		void ModernEnd()
		{
			if (pUI && pUI->fontSmall()) ImGui::PopFont();
			ImGui::PopStyleColor();
			ImGui::PopStyleVar(3);
			ImGui::End();
		}

		void ModernHeader(const char* active)
		{
			const ImVec2 display = ImGui::GetIO().DisplaySize;
			ImGui::SetCursorPos(ImVec2(28.0f, 20.0f));
			ImGui::TextColored(ImVec4(0.0f, 0.90f, 0.52f, 1.0f), "CRESTWOOD RP");
			ImGui::SameLine(display.x - 290.0f);
			ImGui::TextColored(ImVec4(0.46f, 0.50f, 0.48f, 1.0f), "CRÉATION DU PERSONNAGE");
			ImGui::SetCursorPos(ImVec2(28.0f, 56.0f));
			const char* tabs[] = { "DONNÉES", "APPARENCE", "FINALISER" };
			for (int i = 0; i < 3; ++i) {
				if (i > 0) ImGui::SameLine(0.0f, 25.0f);
				const bool selected = std::strcmp(tabs[i], active) == 0;
				ImGui::PushStyleColor(ImGuiCol_Text, selected ? ImVec4(0.0f, 0.92f, 0.52f, 1.0f) : ImVec4(0.40f, 0.44f, 0.42f, 1.0f));
				ImGui::Text("%s", tabs[i]);
				ImGui::PopStyleColor();
			}
			ImGui::GetWindowDrawList()->AddLine(ImVec2(28.0f, 78.0f), ImVec2(display.x - 28.0f, 78.0f), IM_COL32(0, 210, 120, 100), 1.0f);
		}

		void RenderNewCharModal()
		{
			if (!ModernBegin("##new_character_modern")) {
				ImGui::End();
				return;
			}
			ModernHeader("DONNÉES");
			const ImVec2 display = ImGui::GetIO().DisplaySize;
			const float panelW = std::min(520.0f, display.x * 0.43f);
			ImGui::SetCursorPos(ImVec2(34.0f, 122.0f));
			ImGui::BeginChild("modern_register_panel", ImVec2(panelW, display.y - 165.0f), false);
			ImGui::TextColored(ImVec4(0.0f, 0.85f, 0.45f, 1.0f), "COMMENCEZ ICI");
			ImGui::Spacing();
			ImGui::TextColored(ImVec4(0.96f, 0.98f, 0.97f, 1.0f), "Créez votre personnage");
			ImGui::TextColored(ImVec4(0.45f, 0.50f, 0.48f, 1.0f), "Remplissez vos informations pour entrer à Crestwood.");
			ImGui::Spacing();
			ImGui::Spacing();
			const float fullW = panelW - 18.0f;

			ImGui::TextColored(ImVec4(0.48f, 0.53f, 0.50f, 1.0f), "GENRE");
			ImGui::PushStyleColor(ImGuiCol_Button, g_gender == 0 ? ImVec4(0.0f, 0.72f, 0.38f, 1.0f) : ImVec4(0.015f, 0.025f, 0.023f, 1.0f));
			ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.0f, 0.55f, 0.30f, 1.0f));
			if (ImGui::Button("HOMME", ImVec2(fullW * 0.48f, 43.0f))) { g_gender = 0; PreviewGender(0); }
			ImGui::PopStyleColor(2);
			ImGui::SameLine();
			ImGui::PushStyleColor(ImGuiCol_Button, g_gender == 1 ? ImVec4(0.0f, 0.72f, 0.38f, 1.0f) : ImVec4(0.015f, 0.025f, 0.023f, 1.0f));
			ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.0f, 0.55f, 0.30f, 1.0f));
			if (ImGui::Button("FEMME", ImVec2(fullW * 0.48f, 43.0f))) { g_gender = 1; PreviewGender(1); }
			ImGui::PopStyleColor(2);
			ImGui::Spacing();

			std::string nameLabel = g_nameBuf[0] ? g_nameBuf : "Saisissez votre prénom";
			if (g_editTarget == EditTarget::Name) { const std::string live = FieldLiveText(); if (!live.empty()) nameLabel = live; }
			ImGui::TextColored(ImVec4(0.48f, 0.53f, 0.50f, 1.0f), "PRÉNOM");
			ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.015f, 0.025f, 0.023f, 0.96f));
			ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.025f, 0.09f, 0.065f, 1.0f));
			if (ImGui::Button(nameLabel.c_str(), ImVec2(fullW, 43.0f))) BeginEdit(EditTarget::Name);
			ImGui::PopStyleColor(2);
			ImGui::Spacing();

			std::string lastLabel = g_lastNameBuf[0] ? g_lastNameBuf : "Saisissez votre nom de famille";
			if (g_editTarget == EditTarget::LastName) { const std::string live = FieldLiveText(); if (!live.empty()) lastLabel = live; }
			ImGui::TextColored(ImVec4(0.48f, 0.53f, 0.50f, 1.0f), "NOM DE FAMILLE");
			ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.015f, 0.025f, 0.023f, 0.96f));
			ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.025f, 0.09f, 0.065f, 1.0f));
			if (ImGui::Button(lastLabel.c_str(), ImVec2(fullW, 43.0f))) BeginEdit(EditTarget::LastName);
			ImGui::PopStyleColor(2);
			ImGui::Spacing();

			std::string birthLabel = "JJ / MM / AAAA";
			if (g_day > 0 && g_month > 0 && g_year > 0) { char tmp[16]; std::snprintf(tmp, sizeof(tmp), "%02d / %02d / %04d", g_day, g_month, g_year); birthLabel = tmp; }
			if (g_editTarget == EditTarget::Birth) { const std::string live = FieldLiveText(); if (!live.empty()) birthLabel = live; }
			ImGui::TextColored(ImVec4(0.48f, 0.53f, 0.50f, 1.0f), "DATE DE NAISSANCE");
			ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.015f, 0.025f, 0.023f, 0.96f));
			ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.025f, 0.09f, 0.065f, 1.0f));
			if (ImGui::Button(birthLabel.c_str(), ImVec2(fullW, 43.0f))) BeginEdit(EditTarget::Birth);
			ImGui::PopStyleColor(2);
			ImGui::Spacing();
			ImGui::TextColored(ImVec4(0.34f, 0.39f, 0.37f, 1.0f), "Vous pourrez personnaliser vêtements, cheveux et visage à l'étape suivante.");
			ImGui::EndChild();

			const bool valid = g_nameBuf[0] != 0 && g_lastNameBuf[0] != 0 && g_day >= 1 && g_day <= 31 && g_month >= 1 && g_month <= 12 && g_year >= 1900 && g_year <= 2026;
			ImGui::SetCursorPos(ImVec2(34.0f, display.y - 70.0f));
			ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.02f, 0.08f, 0.055f, 1.0f));
			if (ImGui::Button("RETOUR", ImVec2(130.0f, 44.0f))) { g_editTarget = EditTarget::None; g_pedHidden = !g_hasChar; SetPedVisible(!g_pedHidden); g_state = State::Select; }
			ImGui::PopStyleColor();
			ImGui::SameLine(174.0f);
			ImGui::PushStyleColor(ImGuiCol_Button, valid ? ImVec4(0.0f, 0.82f, 0.43f, 1.0f) : ImVec4(0.08f, 0.14f, 0.11f, 1.0f));
			if (ImGui::Button(valid ? "CONTINUER   ->" : "REMPLISSEZ LES CHAMPS", ImVec2(panelW - 180.0f, 44.0f)) && valid) { g_editTarget = EditTarget::None; g_state = State::Waiting; CharacterCreator::SendCreateRequest(); }
			ImGui::PopStyleColor();
			ModernEnd();
		}

		void RenderCreateWindow()
		{
			if (!ModernBegin("##character_appearance_modern")) {
				ImGui::End();
				return;
			}
			ModernHeader("APPARENCE");
			const ImVec2 display = ImGui::GetIO().DisplaySize;
			const float panelW = std::min(620.0f, display.x * 0.52f);
			ImGui::SetCursorPos(ImVec2(34.0f, 112.0f));
			ImGui::BeginChild("modern_appearance_panel", ImVec2(panelW, display.y - 180.0f), false);
			ImGui::TextColored(ImVec4(0.0f, 0.85f, 0.45f, 1.0f), g_testMode ? "MODE TEST - VÊTEMENTS" : "DEUXIÈME ÉTAPE");
			ImGui::Spacing();
			ImGui::TextColored(ImVec4(0.96f, 0.98f, 0.97f, 1.0f), "Personnalisez votre personnage");
			ImGui::TextColored(ImVec4(0.45f, 0.50f, 0.48f, 1.0f), g_testMode ? "Aperçu local : rien n'est envoyé au serveur ni enregistré." : "Choisissez l'apparence qui vous représente.");
			ImGui::Spacing();
			if (g_testMode) {
				// En mode test, on peut changer de genre pour essayer les deux systèmes.
				const float halfW = (panelW - 18.0f) * 0.48f;
				ImGui::PushStyleColor(ImGuiCol_Button, g_gender == 0 ? ImVec4(0.0f, 0.72f, 0.38f, 1.0f) : ImVec4(0.015f, 0.025f, 0.023f, 1.0f));
				if (ImGui::Button("HOMME", ImVec2(halfW, 38.0f))) { g_gender = 0; PreviewGender(0); }
				ImGui::PopStyleColor();
				ImGui::SameLine();
				ImGui::PushStyleColor(ImGuiCol_Button, g_gender == 1 ? ImVec4(0.0f, 0.72f, 0.38f, 1.0f) : ImVec4(0.015f, 0.025f, 0.023f, 1.0f));
				if (ImGui::Button("FEMME", ImVec2(halfW, 38.0f))) { g_gender = 1; PreviewGender(1); }
				ImGui::PopStyleColor();
				ImGui::Spacing();
			}
			ImGui::BeginChild("create_scroll", ImVec2(0.0f, 0.0f), false, ImGuiWindowFlags_AlwaysVerticalScrollbar);
			ClothesCustomization::RenderPanel();
			ImGui::EndChild();
			ImGui::EndChild();

			ImGui::SetCursorPos(ImVec2(34.0f, display.y - 70.0f));
			ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.0f, 0.82f, 0.43f, 1.0f));
			if (g_testMode) {
				// Pas d'envoi au serveur : on referme simplement le panneau.
				if (ImGui::Button("FERMER (TEST)", ImVec2(panelW, 48.0f))) { CharacterCreator::CloseAll(); }
			}
			else if (ImGui::Button("FINALISER LE PERSONNAGE   ->", ImVec2(panelW, 48.0f))) { g_state = State::Waiting; CharacterCreator::SendSaveCharacter(); }
			ImGui::PopStyleColor();
			ModernEnd();
		}

	void RenderWaiting()
	{
		if (!CreatorBegin("##waiting", 300.0f, 80.0f,
				ImGuiWindowFlags_NoTitleBar)) {
			ImGui::End();
			return;
		}
		ImGui::Text("Veuillez patienter...");
		CreatorEnd();
	}
}

// ============================================================
// Widget receptor do teclado do launcher
// ============================================================
void CreatorKeyboardHost::keyboardEvent(const std::string& input)
{
	CommitEdit(input);
}

void CreatorKeyboardHost::keyboardCancelEvent()
{
	// Teclado fechado sem enviar: apenas desativa a edição, mantendo o valor
	// anterior (igual ao comportamento do EditBox do launcher).
	g_editTarget = EditTarget::None;
}

// ============================================================
// API pública
// ============================================================
void CharacterCreator::OpenSelect(const std::string& json)
{
	g_hasChar = JsonBool(json, "hasChar", false);
	g_charName = JsonString(json, "name", "");
	g_day = JsonInt(json, "day", 0);
	g_month = JsonInt(json, "month", 0);
	g_year = JsonInt(json, "year", 0);
	g_cash = JsonInt(json, "cash", 0);
	g_bank = JsonInt(json, "bank", 0);

	// A seleção abre no MESMO instante do spawn do cliente (que solta o ped
	// ~0.5m acima do chão — por isso a pose de queda presa). Este handler
	// roda na THREAD DE REDE, no meio do spawn/streaming do modelo do boneco:
	// mexer no ped/texturas aqui disputa com a thread do jogo e crasha (os
	// logs mostram o TryLoadModel do spawn rodando junto). Então só guardamos
	// os dados e marcamos o setup pendente — o Render (thread do jogo, o
	// mesmo lugar onde o "+" troca o modelo sem crash) executa o reset do
	// boneco no primeiro tick: limpa a tarefa de queda, recria o modelo e
	// aplica o visual salvo (ou zera pro padrão se o slot estiver vazio /
	// personagem foi DELETADO).
	g_testMode = false;
	g_selectJson = json;
	g_selectSetupPending = true;
	g_selectOpenTick = GetTickCount();
	// A repetição ~600ms do reset do modelo só faz sentido com personagem
	// salvo; slot vazio não mexe no modelo (ver setup no Render).
	g_selectModelRetries = g_hasChar ? 1 : 0;

	// Sem personagem salvo (conta nova): o boneco fica escondido na seleção
	// até apertar "+". Com personagem, aparece normal. Só muda a flag aqui
	// (thread de rede) — quem aplica no ped é o tick de 300ms do Render
	// (thread do jogo), que roda logo em seguida.
	g_pedHidden = !g_hasChar;
	g_state = State::Select;

	// SPAWN DIRECT : le joueur a deja un personnage -> pas d'ecran de selection,
	// on demande le spawn tout de suite (le serveur repond avec APPLY_LOOK/CLOSE).
	if (g_hasChar) {
		g_pedHidden = false;
		g_selectSetupPending = false;
		g_selectModelRetries = 0;
		g_state = State::Waiting;
		g_autoSpawnTick = GetTickCount();
		CharacterCreator::SendSpawnRequest();
	}

	// Le bouton de spawn (NASCER) est affiche : on ferme l'ecran de chargement du launcher.
	if (pJavaWrapper) pJavaWrapper->HideLoadingScreen();
}

void CharacterCreator::OpenCreate()
{
	g_autoSpawnTick = 0;
	g_testMode = false;
	g_selectModelRetries = 0;
	g_selectSetupPending = false;
	g_pedHidden = false;
	g_state = State::Creating;

	if (pJavaWrapper) pJavaWrapper->HideLoadingScreen();
}

void CharacterCreator::ApplyLook(const std::string& json)
{
	g_autoSpawnTick = 0;
	g_testMode = false;
	g_selectModelRetries = 0;
	g_selectSetupPending = false;
	ApplyLookFromJson(json);
	if (g_editTarget != EditTarget::None && pUI && pUI->keyboard()) {
		pUI->keyboard()->hide();
	}
	g_editTarget = EditTarget::None;
	g_pedHidden = false;
	g_state = State::Hidden;
}

void CharacterCreator::CloseAll()
{
	g_autoSpawnTick = 0;
	g_testMode = false;
	g_selectModelRetries = 0;
	g_selectSetupPending = false;
	if (g_editTarget != EditTarget::None && pUI && pUI->keyboard()) {
		pUI->keyboard()->hide();
	}
	g_editTarget = EditTarget::None;
	g_pedHidden = false;
	g_state = State::Hidden;
}

void CharacterCreator::RequestOpenTest()
{
	g_testOpenRequested.store(true);
}

bool CharacterCreator::IsVisible()
{
	return g_state != State::Hidden;
}

void CharacterCreator::Render()
{
	// Demande du bouton de test du HUD : traitée ici, sur le thread du jeu.
	// Un second appui sur le bouton referme le panneau.
	if (g_testOpenRequested.exchange(false)) {
		if (g_testMode && g_state != State::Hidden) {
			CloseAll();
		}
		else if (g_state == State::Hidden) {
			OpenTestNow();
		}
	}

	if (g_state == State::Hidden) return;

	// Repli du spawn direct : pas de reponse serveur apres 8 s -> ecran de selection
	if (g_state == State::Waiting && g_autoSpawnTick != 0 && GetTickCount() - g_autoSpawnTick > 8000) {
		g_autoSpawnTick = 0;
		g_state = State::Select;
	}

	// Garante que o boneco fique de frente pra câmera enquanto a criação está
	// aberta (reaplica a cada 300ms, sem brigar com a animação do jogo). Neste
	// mesmo tick também reaplicamos a visibilidade do boneco (escondido na
	// seleção sem personagem até apertar "+") — só ~4x/s, sem cutucar o ped a
	// cada frame.
	const uint32_t now = GetTickCount();
	if (now - g_lastFaceCameraTick > 300) {
		g_lastFaceCameraTick = now;
		SetPedVisible(!g_pedHidden);
		FacePlayerToCamera();
		if (!g_testMode) FallGuard(); // en mode test, le joueur reste là où il est

		// Setup da seleção na thread do JOGO: roda 1x no primeiro tick depois
		// que o servidor abriu a tela. Só para conta COM personagem (o reset
		// comprovado: limpa a pose de queda e reaplica o visual salvo). Para
		// conta SEM personagem (slot vazio) NÃO fazemos nada aqui: trocar o
		// modelo/texturas no mesmo instante do spawn (modelos ainda
		// streamando — TryLoadModel no log) é o que crashava; o boneco já
		// fica escondido e o "+" (seguro, bem depois do spawn) chama o
		// PreviewGender na hora de criar.
		if (g_state == State::Select && g_selectSetupPending && g_hasChar) {
			g_selectSetupPending = false;
			ClearStuckFall();
			ReapplyPedModel();
			ApplyLookFromJson(g_selectJson);
		}
		else if (g_state == State::Select && g_selectModelRetries > 0 && g_hasChar &&
			now - g_selectOpenTick > 600)
		{
			g_selectModelRetries = 0;
			if (ReapplyPedModel()) {
				ClothesCustomization::ApplyCurrent();
			}
		}
	}

	// Estilo escuro baseado nas referências
	ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0.08f, 0.10f, 0.13f, 0.96f));
	ImGui::PushStyleColor(ImGuiCol_Border, ImVec4(1.0f, 1.0f, 1.0f, 0.25f));
	ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.20f, 0.23f, 0.28f, 1.0f));
	ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(0.28f, 0.31f, 0.36f, 1.0f));
	ImGui::PushStyleColor(ImGuiCol_ButtonActive, ImVec4(0.0f, 0.45f, 0.90f, 1.0f));
	ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.94f, 0.95f, 0.97f, 1.0f));
	ImGui::PushStyleColor(ImGuiCol_TitleBg, ImVec4(0.06f, 0.08f, 0.10f, 1.0f));
	ImGui::PushStyleColor(ImGuiCol_TitleBgActive, ImVec4(0.06f, 0.08f, 0.10f, 1.0f));

	switch (g_state) {
		case State::Select:
			RenderSelectWindow();
			break;
		case State::NewCharModal:
			RenderNewCharModal();
			break;
		case State::Creating:
			RenderCreateWindow();
			break;
		case State::Waiting:
			RenderWaiting();
			break;
		default:
			break;
	}

	ImGui::PopStyleColor(8);
}

void CharacterCreator::SendCreateRequest()
{
	if (!pNetGame) return;
	pNetGame->SendCharCreateRequest(
			static_cast<uint8_t>(g_gender),
			g_nameBuf,
			g_lastNameBuf,
			static_cast<uint8_t>(g_day),
			static_cast<uint8_t>(g_month),
			static_cast<uint16_t>(g_year));
}

void CharacterCreator::SendSaveCharacter()
{
	if (!pNetGame) return;
	pNetGame->SendCharSave(ClothesCustomization::Look());
}

void CharacterCreator::SendSpawnRequest()
{
	if (!pNetGame) return;
	pNetGame->SendCharSpawnRequest();
}
