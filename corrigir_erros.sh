#!/bin/bash
# Script de Correção Automatizada - Café com Água
# Autor: Assistente IA
# Data: 2025-11-07

echo "🔧 Iniciando correção automatizada dos erros..."
echo ""

# Cores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

PROJECT_ROOT="."
ERRORS_FIXED=0
ERRORS_FAILED=0

# Função para logging
log_success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((ERRORS_FIXED++))
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
    ((ERRORS_FAILED++))
}

# Backup dos arquivos antes das alterações
backup_files() {
    echo "📦 Criando backup dos arquivos..."
    BACKUP_DIR="backup_$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    cp -r "$PROJECT_ROOT/app/src/main/java" "$BACKUP_DIR/" 2>/dev/null
    cp -r "$PROJECT_ROOT/app/src/main/res" "$BACKUP_DIR/" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        log_success "Backup criado em: $BACKUP_DIR"
    else
        log_warning "Não foi possível criar backup completo"
    fi
    echo ""
}

# Grupo 1: Corrigir HistoryAdapterWithAds.kt
fix_history_adapter() {
    echo "🔄 Grupo 1: Corrigindo HistoryAdapterWithAds.kt..."
    
    FILE="$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/adapters/Historyadapterwithads.kt"
    
    if [ ! -f "$FILE" ]; then
        log_error "Arquivo não encontrado: $FILE"
        return
    fi
    
    # Corrigir linha 159
    sed -i 's/binding\.textViewNomeAgua/binding.textViewAgua/g' "$FILE"
    log_success "Corrigido: textViewNomeAgua → textViewAgua"
    
    # Corrigir linha 160
    sed -i 's/binding\.textViewFonteAgua/binding.textViewFonte/g' "$FILE"
    log_success "Corrigido: textViewFonteAgua → textViewFonte"
    
    # Corrigir linha 176
    sed -i 's/binding\.textViewQualidade\.text/binding.textViewQualidadeGeral.text/g' "$FILE"
    log_success "Corrigido: textViewQualidade → textViewQualidadeGeral"
    
    echo ""
}

# Grupo 2: Corrigir OnboardingAdapter.kt
fix_onboarding_adapter() {
    echo "🔄 Grupo 2: Corrigindo OnboardingAdapter.kt..."
    
    FILE="$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/adapters/Onboardingadapter.kt"
    
    if [ ! -f "$FILE" ]; then
        log_error "Arquivo não encontrado: $FILE"
        return
    fi
    
    # Corrigir linha 23
    sed -i 's/textMessage\.text/textDescription.text/g' "$FILE"
    log_success "Corrigido: textMessage → textDescription"
    
    # Corrigir linha 24
    sed -i 's/imageIllustration\.setImageResource/imageIcon.setImageResource/g' "$FILE"
    log_success "Corrigido: imageIllustration → imageIcon"
    
    echo ""
}

# Grupo 3: Adicionar strings ao strings.xml
fix_strings_xml() {
    echo "🔄 Grupo 3: Adicionando strings ao strings.xml..."
    
    FILE="$PROJECT_ROOT/app/src/main/res/values/strings.xml"
    
    if [ ! -f "$FILE" ]; then
        log_error "Arquivo não encontrado: $FILE"
        return
    fi
    
    # Verifica se as strings já existem
    if grep -q "onboarding_title_1" "$FILE"; then
        log_warning "Strings de onboarding já existem, pulando..."
        echo ""
        return
    fi
    
    # Adiciona as strings antes da tag de fechamento </resources>
    STRINGS_TO_ADD='
    <!-- Onboarding -->
    <string name="onboarding_title_1">Bem-vindo ao Café com Água</string>
    <string name="onboarding_message_1">Avalie a qualidade da sua água e descubra como ela afeta o sabor do seu café</string>
    
    <string name="onboarding_title_2">Padrões SCA</string>
    <string name="onboarding_message_2">Utilizamos os padrões da Specialty Coffee Association para análise profissional</string>
    
    <string name="onboarding_title_3">Recursos Premium</string>
    <string name="onboarding_message_3">Desbloqueie funcionalidades avançadas e otimize sua experiência</string>
    
    <string name="onboarding_icon">Ícone de onboarding</string>'
    
    # Insere antes da última linha (</resources>)
    sed -i "$ i\\$STRINGS_TO_ADD" "$FILE"
    
    log_success "Strings de onboarding adicionadas"
    echo ""
}

# Grupo 4: Corrigir importações do Analytics em todos os arquivos
fix_analytics_imports() {
    echo "🔄 Grupo 4: Corrigindo importações do Analytics..."
    
    FILES=(
        "$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/home/HomeActivity.kt"
        "$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/onboarding/Onboardingactivity.kt"
        "$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/parameters/ParametersActivity.kt"
        "$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/waterinput/WaterInputActivity.kt"
    )
    
    for FILE in "${FILES[@]}"; do
        if [ ! -f "$FILE" ]; then
            log_warning "Arquivo não encontrado: $(basename $FILE)"
            continue
        fi
        
        # Adiciona importações estáticas se não existirem
        if ! grep -q "import com.marcos.cafecomagua.app.analytics.Event" "$FILE"; then
            # Encontra a última linha de import
            LAST_IMPORT_LINE=$(grep -n "^import" "$FILE" | tail -1 | cut -d: -f1)
            
            if [ -n "$LAST_IMPORT_LINE" ]; then
                # Adiciona as novas importações após a última importação existente
                sed -i "${LAST_IMPORT_LINE}a\\
import com.marcos.cafecomagua.app.analytics.Event\\
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event" "$FILE"
                
                log_success "Importações adicionadas em: $(basename $FILE)"
            fi
        else
            log_warning "Importações já existem em: $(basename $FILE)"
        fi
        
        # Substitui Event por Category
        sed -i 's/AnalyticsManager\.Category\./Category./g' "$FILE"
        sed -i 's/AnalyticsManager\.Event\./Event./g' "$FILE"
        
        log_success "Referências simplificadas em: $(basename $FILE)"
    done
    
    echo ""
}

# Grupo 5: Corrigir type mismatch no ParametersActivity
fix_parameters_type_mismatch() {
    echo "🔄 Grupo 5: Corrigindo type mismatch em ParametersActivity.kt..."
    
    FILE="$PROJECT_ROOT/app/src/main/java/com/marcos/cafecomagua/ui/parameters/ParametersActivity.kt"
    
    if [ ! -f "$FILE" ]; then
        log_error "Arquivo não encontrado: $FILE"
        return
    fi
    
    # Procura por padrões como: editText.text = someString
    # e substitui por: editText.setText(someString)
    
    # Esta é uma correção genérica - pode precisar de ajuste manual
    log_warning "⚠️  Type mismatch nas linhas 135-136 requer verificação manual"
    log_warning "    Verifique se está usando .setText() ao invés de .text ="
    
    echo ""
}

# Função principal
main() {
    echo "╔════════════════════════════════════════════════════════╗"
    echo "║  Script de Correção Automatizada - Café com Água      ║"
    echo "╚════════════════════════════════════════════════════════╝"
    echo ""
    
    # Verifica se está no diretório correto
    if [ ! -d "app/src/main" ]; then
        log_error "Execute este script na raiz do projeto Android!"
        exit 1
    fi
    
    # Cria backup
    backup_files
    
    # Executa correções
    fix_history_adapter
    fix_onboarding_adapter
    fix_strings_xml
    fix_analytics_imports
    fix_parameters_type_mismatch
    
    # Relatório final
    echo "═══════════════════════════════════════════════════════"
    echo "📊 RELATÓRIO FINAL"
    echo "═══════════════════════════════════════════════════════"
    echo -e "${GREEN}✅ Correções aplicadas com sucesso: $ERRORS_FIXED${NC}"
    if [ $ERRORS_FAILED -gt 0 ]; then
        echo -e "${RED}❌ Erros encontrados: $ERRORS_FAILED${NC}"
    fi
    echo ""
    echo "⚡ Próximos passos:"
    echo "   1. Revise as mudanças no Git/controle de versão"
    echo "   2. Execute: ./gradlew clean"
    echo "   3. Execute: ./gradlew build"
    echo "   4. Verifique warnings e erros restantes"
    echo ""
    echo "📁 Backup dos arquivos originais em: $BACKUP_DIR"
    echo ""
}

# Executa script
main
